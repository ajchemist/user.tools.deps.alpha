(ns user.tools.deps.jar
  (:require
   [clojure.string :as str]
   [clojure.java.io :as jio]
   [user.java.io.alpha :as u.jio]
   [clojure.tools.deps.alpha.reader :as deps.reader]
   [user.tools.deps.io :as io]
   [user.tools.deps.alpha :as u.deps]
   [user.tools.deps.maven.alpha :as maven]
   [user.tools.deps.util.jar :as util.jar]
   )
  (:import
   java.io.File
   java.io.OutputStream
   java.nio.file.Files
   java.nio.file.Path
   java.nio.file.FileSystem
   java.util.jar.JarEntry
   java.util.jar.Manifest
   ))


(set! *warn-on-reflection* true)


;;


(defn make-jarname
  ^String
  [artifact-id {:keys [:mvn/version classifier extension]}]
  (let [classifier (when classifier (str "-" (name classifier)))
        version    (when version (str "-" version))
        extension  (or extension ".jar")]
    (str artifact-id version classifier extension)))


(defn make-jarpath
  ^Path
  [artifact-id maven-coords target-path]
  (let [target-path (u.jio/path target-path)]
    (.resolve target-path (make-jarname artifact-id maven-coords))))


(defn get-jar-filesystem
  ^FileSystem
  [artifact-id maven-coords target-path]
  (util.jar/getjarfs (make-jarpath artifact-id maven-coords target-path)))


;;


(defn manifest-mf-operation
  [^Manifest manifest]
  (when manifest
    {:op       :write
     :path     "META-INF/MANIFEST.MF"
     :write-fn (fn [^OutputStream os] (. manifest write os))}))


(defn pom-xml-operation
  [pom-path group-id artifact-id]
  (when (u.jio/file? pom-path)
    {:op :copy :src pom-path :path (str "META-INF/maven/" group-id "/" artifact-id "/pom.xml")}))


(defn pom-properties-operation
  [pom-properties group-id artifact-id]
  (when pom-properties
    {:op       :write
     :path     (str "META-INF/maven/" group-id "/" artifact-id "/pom.properties")
     :write-fn (fn [os] (maven/store-pom-properties os pom-properties nil))}))


(defn deps-edn-operation
  [group-id artifact-id]
  (when (u.jio/file? "deps.edn")
    {:op :copy :src "deps.edn" :path (str "META-INF/user.tools.deps.alpha/" group-id "/" artifact-id "/deps.edn")}))


(defn check-non-maven-dependencies
  [{:keys [deps]}]
  (doseq [[lib {:keys [:mvn/version] :as dep}] deps]
    (when (nil? version)
      (throw
        (ex-info
          "All dependencies must be Maven-based. Use the \"allow-all-dependencies?\" option to continue building the jar anyway. When using the \"allow-all-dependencies?\" option, only Maven-based depedencies are added to the pom.xml file."
          {:lib lib
           :dep dep})))))


;; * predicate


(defn entry-excluded?
  [^String entry-name]
  (or (str/starts-with? entry-name "META-INF/leiningen")
      (str/starts-with? entry-name "META-INF/user.tools.deps.alpha")
      (some
        #(re-matches % entry-name)
        [#"(?i)(?:AUTHOR|LICENSE|COPYRIGHT)(?:\..*)?"
         #"(?i)META-INF/.*\.(?:MF|SF|RSA|DSA)"
         #"(?i)META-INF/(?:INDEX\.LIST|DEPENDENCIES|NOTICE|LICENSE)(?:\.txt|\.md|\.html)?"])))


(defn dotfiles-exclusion-predicate
  [{:keys [path]}]
  (let [path (u.jio/path path)]
    (.startsWith path ".")))


(defn emacs-backups-exclusion-predicate
  [{:keys [path]}]
  (let [path (u.jio/path path)]
    (or (.endsWith path "~")
        (.startsWith path "#"))))


;; * uber


(defn copy-jar
  [jarpath ^Path dest-path]
  (io/consume-jar
    (str jarpath)
    (fn [is ^JarEntry entry]
      (let [entry-name (.getName entry)
            target     (.resolve dest-path entry-name)]
        (if (.isDirectory entry)
          (u.jio/mkdir target)
          (when-not (entry-excluded? entry-name)
            (io/copy! is (doto target (u.jio/mkparents)))))))))


(defn copy-directory
  [src ^Path dest-path]
  (let [src-path (u.jio/path src)]
    (Files/walkFileTree
      src-path
      (u.jio/make-file-visitor
        (fn [[path attr]]
          (io/copy! path (doto (.resolve dest-path (str (.relativize src-path path))) (u.jio/mkparents))))))))


(defn uber-classpath
  [^String classpath ^Path dest-path]
  (run!
    (fn [path]
      (cond
        (str/ends-with? path ".jar") (copy-jar path dest-path)
        (u.jio/directory? path)      (copy-directory path dest-path)
        :else                        (println "[uber-classpath] no uber method:" path)))
    (str/split classpath (re-pattern File/pathSeparator))))


;; * jar


(defn jar
  "Bundles project resources into a jar file. This function also generates maven description files. By default, this function ensures that all the project dependencies are maven based.
  - lib: A symbol naming the library.
  - maven-coords: A map with the same format than tools.deps maven coordinates.
  - out-path: The path of the produced jar file. When not provided, a default out-path is generated from the lib and maven coordinates.
  - main: A namespace to be added to the \"Main\" entry to the jar manifest. Default to nil.
  - manifest: A map of additionel entries to the jar manifest. Values of the manifest map can be maps to represent manifest sections. By default, the jar manifest contains the \"Created-by\", \"Built-By\" and \"Build-Jdk\" entries.
  - deps: The dependencies of the project. deps have the same format than the :deps entry of a tools.deps map. Dependencies are copied to the pom.xml file produced while generating the jar file. Default to the deps.edn dependencies of the project (excluding the system-level and user-level deps.edn dependencies).
  - mvn/repos: Repositories to be copied to the pom.xml file produced while generating the jar. Must have same format than the :mvn/repos entry of deps.edn. Default to nil.
  - exclusion-predicates: Predicates to exclude operations that would otherwise been operated to the jar. Each predicate takes a parameter: file-operation. Default to predicates that excludes dotfiles and emacs backup files.
  - allow-all-dependencies?: A boolean that can be set to true to allow any types of dependency, such as local or git dependencies. Default to false, in which case only maven dependencies are allowed - an exception is thrown when this is not the case. When set to true, the jar is produced even in the presence of non-maven dependencies, but only maven dependencies are added to the jar."
  ([lib maven-coords]
   (jar lib maven-coords nil nil))
  ([lib maven-coords
    paths
    {:keys [out-path
            target-path
            jarname
            compile-path
            deps-map
            main
            manifest
            pom-path
            pom-properties
            extra-operations
            exclusion-predicates
            allow-all-dependencies?]
     :or   {exclusion-predicates [dotfiles-exclusion-predicate
                                  emacs-backups-exclusion-predicate]}
     :as   options}]
   (let [[lib version] (if (and pom-path (u.jio/file? pom-path))
                         (let [pom (maven/read-pom pom-path)]
                           [(or lib (symbol (.getGroupId pom) (.getArtifactId pom)))
                            (update maven-coords :mvn/version #(or % (.getVersion pom)))])
                         [lib maven-coords])
         artifact-id   (name lib)
         group-id      (or (namespace lib) artifact-id)
         target-path   (u.jio/path (or target-path "target"))
         out-path      (u.jio/path (or out-path
                                       (and jarname (u.jio/path-resolve target-path jarname))
                                       (make-jarpath artifact-id maven-coords target-path)))
         _             (when-not (str/ends-with? (str out-path) ".jar")
                         (throw
                           (ex-info "out-path must be a jar file"
                             {:out-path out-path})))
         deps-map      (or deps-map (u.deps/deps-map))
         paths         (or paths (:paths deps-map))]
     (when-not allow-all-dependencies?
       (check-non-maven-dependencies deps-map))
     (with-open [jarfs (util.jar/getjarfs out-path)]
       (let [the-manifest   (util.jar/create-manifest main manifest)
             pom-properties (or pom-properties (maven/make-pom-properties lib maven-coords))]
         (io/do-operations
           (u.jio/path jarfs)
           (transduce
             (comp
               (filter map?)
               (remove (fn [op] (some #(% op) exclusion-predicates))))
             conj
             []
             (concat
               [(manifest-mf-operation the-manifest)
                (pom-xml-operation (or pom-path "pom.xml") group-id artifact-id)
                (pom-properties-operation pom-properties group-id artifact-id)
                (deps-edn-operation group-id artifact-id)]
               (when compile-path (u.jio/paths-copy-operations [compile-path]))
               (when-not (empty? paths) (u.jio/paths-copy-operations paths))
               extra-operations)))))
     (str out-path))))


(defn maven-jar
  ([pom-path]
   (maven-jar pom-path nil nil nil))
  ([pom-path
    maven-coords
    paths
    {:keys [out-path
            target-path
            jarname
            compile-path
            deps-map
            main
            manifest
            pom-properties
            extra-operations
            exclusion-predicates
            allow-all-dependencies?]
     :or   {exclusion-predicates [dotfiles-exclusion-predicate
                                  emacs-backups-exclusion-predicate]}
     :as   options}]
   (let [pom          (maven/read-pom pom-path)
         artifact-id  (.getArtifactId pom)
         group-id     (.getGroupId pom)
         maven-coords (update maven-coords :mvn/version #(or % (.getVersion pom)))
         target-path  (u.jio/path (or target-path "target"))
         out-path     (u.jio/path (or out-path
                                      (and jarname (u.jio/path-resolve target-path jarname))
                                      (make-jarpath artifact-id maven-coords target-path)))
         _            (when-not (str/ends-with? (str out-path) ".jar")
                        (throw
                          (ex-info "out-path must be a jar file"
                            {:out-path out-path})))
         deps-map     (or deps-map (deps.reader/read-deps ["deps.edn"]))
         paths        (or paths (:paths deps-map))]
     (when-not allow-all-dependencies?
       (check-non-maven-dependencies deps-map))
     (with-open [jarfs (util.jar/getjarfs out-path)]
       (let [the-manifest   (util.jar/create-manifest main manifest)
             pom-properties (or pom-properties (maven/make-pom-properties (keyword group-id artifact-id) maven-coords))]
         (io/do-operations
           (u.jio/path jarfs)
           (transduce
             (comp
               (filter map?)
               (remove (fn [op] (some #(% op) exclusion-predicates))))
             conj
             []
             (concat
               [(manifest-mf-operation the-manifest)
                (pom-xml-operation pom-path group-id artifact-id)
                (pom-properties-operation pom-properties group-id artifact-id)
                (deps-edn-operation group-id artifact-id)]
               (when compile-path (u.jio/paths-copy-operations [compile-path]))
               (when-not (empty? paths) (u.jio/paths-copy-operations paths))
               extra-operations)))))
     (str out-path))))


(defn uberjar
  [jarpath classpath]
  (with-open [jarfs (util.jar/getjarfs jarpath)]
    (uber-classpath classpath (u.jio/path jarfs)))
  jarpath)


(set! *warn-on-reflection* false)


(comment
  (jar 'user.tools.deps.alpha {:mvn/version "0.0.0"} nil {})
  )
