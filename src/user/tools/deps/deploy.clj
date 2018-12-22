(ns user.tools.deps.deploy
  (:require
   [clojure.string :as str]
   [clojure.java.io :as jio]
   [clojure.tools.deps.alpha.util.maven :as util.maven]
   [clojure.tools.deps.alpha.util.io :refer [printerrln]]
   [user.apache.maven.pom.alpha :as pom]
   [user.tools.deps.maven.alpha :as maven]
   )
  (:import
   org.apache.maven.settings.DefaultMavenSettingsBuilder
   org.apache.maven.settings.building.DefaultSettingsBuilderFactory
   org.apache.maven.settings.crypto.DefaultSettingsDecrypter
   org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest
   org.apache.maven.settings.crypto.SettingsDecrypter
   org.eclipse.aether.deployment.DeployRequest
   org.eclipse.aether.repository.RemoteRepository$Builder
   org.eclipse.aether.transfer.TransferEvent
   org.eclipse.aether.transfer.TransferEvent$RequestType
   org.eclipse.aether.transfer.TransferListener
   org.eclipse.aether.transfer.TransferResource
   org.eclipse.aether.transfer.TransferResource
   org.eclipse.aether.util.repository.AuthenticationBuilder
   ))


(set! *warn-on-reflection* true)


(defn- set-settings-builder
  [^DefaultMavenSettingsBuilder default-builder settings-builder]
  (doto (.. default-builder getClass (getDeclaredField "settingsBuilder"))
    (.setAccessible true)
    (.set default-builder settings-builder)))


(def ^org.apache.maven.settings.Settings get-settings @(resolve 'util.maven/get-settings))


(def ^TransferListener console-listener
  (reify TransferListener
    (transferStarted [_ event]
      (let [event    ^TransferEvent event
            resource (.getResource event)
            name     (.getResourceName resource)]
        (case (.. event getRequestType name)
          ("GET" "GET_EXISTENCE") (printerrln "Downloading:" name "from" (.getRepositoryUrl resource))
          "PUT"                   (printerrln "Uploading:" name "to" (.getRepositoryUrl resource))
          nil)))
    (transferCorrupted [_ event]
      (printerrln "Download corrupted:" (.. ^TransferEvent event getException getMessage)))
    (transferFailed [_ event]
      ;; This happens when Maven can't find an artifact in a particular repo
      ;; (but still may find it in a different repo), ie this is a common event
      #_(printerrln "Download failed:" (.. ^TransferEvent event getException getMessage)))))


(defn remote-repo
  [[id {:keys [url]}] credentials]
  (let [repository (RemoteRepository$Builder. id "default" url)
        ^org.apache.maven.settings.Server server-setting
        (first (filter
                 #(.equalsIgnoreCase ^String id (.getId ^org.apache.maven.settings.Server %))
                 (.getServers (get-settings))))
        ^String username    (or (:username credentials) (when server-setting (.getUsername server-setting)))
        ^String password    (or (:password credentials) (when server-setting (.getPassword server-setting)))
        ^String private-key (or (:private-key credentials) (when server-setting (.getPassword server-setting)))
        ^String passphrase  (or (:passphrase credentials) (when server-setting (.getPassphrase server-setting)))]
    (-> repository
      (.setAuthentication (.build
                            (doto (AuthenticationBuilder.)
                              (.addUsername username)
                              (.addPassword password)
                              (.addPrivateKey private-key passphrase))))
      (.build))))


(defn make-artifact
  [lib version {:keys [file-path] :as artifact}]
  (let [artifact (maven/artifact-with-default-extension artifact)]
    (-> (util.maven/coord->artifact lib (assoc artifact :mvn/version version))
      (.setFile (jio/file (str file-path))))))


(defn check-for-snapshot-deps
  [{:keys [version] :as project-map} deps]
  (when (and (not (str/index-of version "SNAPSHOT")))
    (doseq [{:keys [:mvn/version] :as dep} deps]
      (when (str/index-of version "SNAPSHOT")
        (throw (ex-info (str "Release versions may not depend upon snapshots."
                             "\nFreeze snapshots to dated versions or set the"
                             "\"allow-snapshot-deps?\" uberjar option.")
                 {:dependency dep}))))))


(defn ensure-signed-artifacts
  [artifacts version]
  (when-not (str/index-of version "SNAPSHOT")
    (when-not (some (fn [{:keys [extension]}] (str/ends-with? extension ".asc")) artifacts)
      (throw
        (ex-info "Non-snapshot versions of artifacts should be signed. Consider setting the \"allow-unsigned?\" option to process anyway."
          {:artifacts artifacts
           :version   version})))))


(defn deploy
  "Deploys a collection of artifacts to a remote repository. When deploying non-snapshot versions of artifacts, artifacts must be signed, unless the \"allow-unsigned?\" parameter is set to true.
  - lib: A symbol naming the library to be deployed.
  - version: The version of the library to be deployed.
  - artifacts: The collection of artifacts to be deployed. Each artifact must be a map with a :file-path and an optional :extension key. :extension defaults to \"jar\" for jar file and \"pom\" for pom files. Artifacts representing a signature must have a \".asc\" ended extension.
  - repository: A map with an :id and a :url key representing the remote repository where the artifacts are to be deployed. The :id is used to find credentials in the settings.xml file when authenticating to the repository.
  - allow-unsigned?: When set to true, allow deploying non-snapshot versions of unsigned artifacts. Default to false."
  ([lib version artifacts repository]
   (deploy lib version artifacts repository nil))
  ([lib version artifacts repository {:keys [credentials allow-unsigned?]}]
   (when-not allow-unsigned?
     (ensure-signed-artifacts artifacts version))
   (System/setProperty "aether.checksums.forSignature" "true")
   (let [system         (util.maven/make-system)
         session        (with-redefs [util.maven/console-listener console-listener]
                          (util.maven/make-session system util.maven/default-local-repo))
         [lib version]  (if-let [pom-path (some (fn [{:keys [file-path]}] (when (str/ends-with? file-path "pom.xml") file-path)) artifacts)]
                          (let [pom (pom/read-pom pom-path)]
                            [(or lib (symbol (.getGroupId pom) (.getArtifactId pom)))
                             (or version (.getVersion pom))])
                          [lib version])
         artifacts      (map #(make-artifact lib version %) artifacts)
         deploy-request (-> (DeployRequest.)
                          (.setRepository (remote-repo repository credentials)))
         deploy-request (reduce #(.addArtifact ^DeployRequest %1 %2) deploy-request artifacts)]
     (.deploy system session deploy-request))))


(set! *warn-on-reflection* false)


(comment


  (first
    (filter
      #(.equalsIgnoreCase "clojars" (.getId ^org.apache.maven.settings.Server %))
      (.getServers (get-settings))))


  )
