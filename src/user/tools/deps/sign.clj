(ns user.tools.deps.sign
  (:require
   [clojure.tools.deps.alpha.util.maven :as util.maven]
   [user.tools.deps.maven.alpha :as maven]
   [user.tools.deps.util.shell :as util.shell]
   ))


(def ^:const GPG_COMMAND "gpg")


(defn gpg-signing-args
  [file gpg-key]
  (let [key-spec (when gpg-key ["--default-key" gpg-key])]
    `["--batch" "-ab" ~@key-spec "--" ~file]))


(defn gpg-sign-artifact
  "Sign a single artifact. The artifact must be a map with a :file-path key and an optional :extension key. :file-path is the path to th file to be signed. :extension is the artifact packaging type. :extension is optional and defaults to \"jar\" for jar files and \"pom\" for pom files.
  Returns an artifact representing the signature of the input artifact."
  ([artifact]
   (gpg-sign-artifact artifact nil))
  ([artifact
    {:keys [gpg-command gpg-key]
     :or   {gpg-command GPG_COMMAND}}]
   (let [{:keys [file-path extension]} (maven/artifact-with-default-extension artifact)]
     (apply
       util.shell/dosh
       {:error-msg "Error while signing"}
       gpg-command (gpg-signing-args file-path gpg-key))
     `{:file-path ~(str file-path ".asc")
       ~@(when extension [:extension (str extension ".asc")])
       ~@nil})))


(defn gpg-sign
  "Sign a collection of artifacts using the \"gpg\" command.
  - artifacts: A collections of artifacts. Each artifact must be a map with a :file-path key and an optional :extension key. :file-path is the path to th file to be signed. :extension is the artifact packaging type. :extension is optional and defaults to \"jar\" for jar files and \"pom\" for pom files.
  - command: The command used to sign the artifact. Default to \"gpg\".
  - gpg-key: The private key to be used. Default to the first private key found.
  Returns the artifacts representing the signatures of the input artifacts conjoined to the input artifacts."
  ([artifacts]
   (gpg-sign artifacts nil))
  ([artifacts {:keys [gpg-command gpg-key] :as opts}]
   (reduce #(conj %1 (gpg-sign-artifact %2 opts)) artifacts artifacts)))


(comment
  (gpg-sign
    [{:file-path "pom.xml"}]
    {:gpg-key "A26AAA7B1FC11DDC9E9C1EF56289C7A48E1DB476"})
  )
