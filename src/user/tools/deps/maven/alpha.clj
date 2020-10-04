(ns user.tools.deps.maven.alpha
  (:require
   [clojure.java.io :as jio]
   [clojure.string :as str]
   [user.apache.maven.pom.alpha :as pom]
   [user.tools.deps.alpha :as u.deps]
   )
  (:import
   java.io.File
   org.apache.maven.model.Model
   ))


(set! *warn-on-reflection* true)


;; * gen


(defn sync-pom
  "Return `pom-file` path"
  ([lib mvn-coords]
   (sync-pom lib mvn-coords nil))
  ([lib mvn-coords deps-map]
   (sync-pom lib mvn-coords deps-map (jio/file ".")))
  ([lib {:keys [:mvn/version]} deps-map ^File dir]
   (let [{:keys [deps paths :mvn/repos]} (or deps-map (u.deps/project-deps))

         artifact-id (name lib)
         group-id    (or (namespace lib) artifact-id)
         repos       (remove #(str/includes? (-> % val :url) "repo1.maven.org") repos)
         pom-file    (jio/file dir "pom.xml")
         pom         (if (.exists pom-file)
                       (-> (pom/read-pom pom-file)
                         (pom/replace-version version)
                         (pom/replace-deps deps)
                         (pom/replace-build paths)
                         (pom/replace-repos repos))
                       (pom/gen-pom group-id artifact-id version deps paths repos))]
     (pom/write-pom pom-file pom)
     (str pom-file))))


(defn sync-pom-x
  "Sync pom file with a hash-map"
  [{:keys [lib mvn-coords deps-map dir]}]
  (sync-pom lib mvn-coords deps-map dir))


;; * artifact


(defn get-library-from-pom
  [^Model pom]
  (symbol (.getGroupId pom) (.getArtifactId pom)))


(defn artifact-with-default-extension
  [{:keys [file-path] :as artifact}]
  (cond
    (contains? artifact :extension)                artifact
    (str/ends-with? (str file-path) ".jar")        (assoc artifact :extension "jar")
    (str/ends-with? (str file-path) "pom.xml")     (assoc artifact :extension "pom")
    (str/ends-with? (str file-path) ".jar.asc")    (assoc artifact :extension "jar.asc")
    (str/ends-with? (str file-path) "pom.xml.asc") (assoc artifact :extension "pom.asc")
    :else                                          artifact))


;; * finish


(set! *warn-on-reflection* false)


(comment
  (sync-pom
    'user.tools.deps.alpha
    '{:mvn/version "0.0.1-SNAPSHOT"}
    '{:deps      {org.clojure/clojure   {:mvn/version "1.9.0"}
                  user.tools.deps.alpha {:local/root "user.tools.deps.alpha"}}
      ;; :paths ["src"]
      :mvn/repos {"central" {:url "https://repo1.maven.org/maven2/"}
                  "clojars" {:url "https://repo.clojars.org/"}}}
    (jio/file (System/getProperty "java.io.tmpdir")))
  )
