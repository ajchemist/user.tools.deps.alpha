(ns user.tools.deps.install
  (:require
   [clojure.tools.deps.alpha.util.maven :as maven]
   [clojure.java.io :as jio]
   [user.apache.maven.pom.alpha :as pom]
   )
  (:import
   org.eclipse.aether.installation.InstallRequest
   ))


(set! *warn-on-reflection* true)


(defn install
  "Install a jar file into the local maven repository.
  - lib: A symbol naming the library to install. The groupId of the installed library is the namespace of the symbol \"lib\" if lib is a namespaced symbol, or its name if lib is an unqualified symbol. The artifactId of the installed symbol is the name of the \"lib\" symbol.
  - maven-coords: A map representing the maven coordinates of the library, under the same format than the one used by tools.deps.
  - file-path: The path to the jar to be installed.
  - pom-file-path: The path to the pom.xml file to be installed. Default to \"pom.xml\"
  - local-repo: The path to the local maven repository where the library is to be installed. Default to ~/.m2/repository ."
  ([lib maven-coords file-path pom-path]
   (install lib maven-coords file-path pom-path nil))
  ([lib maven-coords file-path pom-path {:keys [local-repo]}]
   (let [local-repo   (or local-repo maven/default-local-repo)
         system       (maven/make-system)
         session      (maven/make-session system local-repo)
         pom-path     (or pom-path "pom.xml")
         _            (assert (.isFile (jio/file pom-path)))
         pom          (pom/read-pom pom-path)
         maven-coords (update maven-coords :mvn/version #(or % (.getVersion pom)))
         lib          (or lib (symbol (.getGroupId pom) (.getArtifactId pom)))
         artifact     (maven/coord->artifact lib maven-coords)
         artifact     (.setFile artifact (jio/file file-path))
         pom-artifact (maven/coord->artifact lib (-> maven-coords (assoc :extension "pom") (dissoc :classifier)))
         pom-artifact (.setFile pom-artifact (jio/file pom-path))]
     (.install system session (-> (InstallRequest.)
                                (.addArtifact artifact)
                                (.addArtifact pom-artifact))))))


(set! *warn-on-reflection* false)



(comment
  (install 'user.tools.deps.alpha
           {:mvn/version "0.0.1-SNAPSHOT"
            :classifier  "cl"}
           "target/user.tools.deps.alpha-0.0.1-SNAPSHOT-cl.jar"
           "pom.xml")
  )
