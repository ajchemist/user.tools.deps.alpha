(ns user.tools.deps.jar
  (:require
   [clojure.string :as str]
   [clojure.java.io :as jio]
   [user.java.io.alpha :as u.jio]
   [user.apache.maven.pom.alpha :as pom]
   [user.tools.deps.maven.alpha :as maven]
   [user.tools.deps.io :as io]
   [user.tools.deps.alpha :as u.deps]
   [user.tools.deps.util.compile :as util.compile]
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
  [artifact-id coords target-dir]
  (let [target-dir (u.jio/path target-dir)]
    (.resolve target-dir (make-jarname artifact-id coords))))


(defn get-jar-filesystem
  ^FileSystem
  [artifact-id coords target-dir]
  (util.jar/getjarfs (make-jarpath artifact-id coords target-dir)))


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
     :write-fn (fn [os] (pom/store-pom-properties os pom-properties nil))}))


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


;; ** uber


(def jar-entry-exclusion-predicates
  [#(str/starts-with? % "META-INF/leiningen")
   #(str/starts-with? % "META-INF/user.tools.deps.alpha")
   #(re-matches #"(?i)(?:AUTHOR|LICENSE|COPYRIGHT)(?:\..*)?" %)
   #(re-matches #"(?i)META-INF/.*\.(?:MF|SF|RSA|DSA)" %)
   #(re-matches #"(?i)META-INF/(?:INDEX\.LIST|DEPENDENCIES|NOTICE|LICENSE)(?:\.txt|\.md|\.html)?" %)])


(def ^:dynamic *exclude-already-compiled* true)
(def ^:dynamic *already-compiled-predicate-verbose* false)


(defn entry-already-compiled?
  [^String entry-name extension]
  (when (str/ends-with? entry-name extension)
    (util.compile/path-already-compiled? (subs entry-name 0 (str/last-index-of entry-name extension)))))


(defn- copy-jar-entry-exclusion-predicates
  []
  (if *exclude-already-compiled*
    (conj
      jar-entry-exclusion-predicates
      (fn [^String entry-name]
        (and
          (or
            (entry-already-compiled? entry-name ".clj")
            (entry-already-compiled? entry-name ".cljc"))
          (do
            (when *already-compiled-predicate-verbose*
              (println "Already compiled:" entry-name))
            true))))
    jar-entry-exclusion-predicates))


;; * uber


(defn copy-jar
  [jarpath ^Path dest-path]
  (io/consume-jar
    (str jarpath)
    (let [entry-exclusion-predicates (copy-jar-entry-exclusion-predicates)]
      (fn [is ^JarEntry entry]
        (let [entry-name (.getName entry)
              target     (.resolve dest-path entry-name)]
          (if (.isDirectory entry)
            (u.jio/mkdir target)
            (when-not (some #(% entry-name) entry-exclusion-predicates)
              (io/copy! is (doto target (u.jio/mkparents))))))))))


(defn copy-directory
  [src ^Path dest-path]
  (let [src-path (u.jio/path src)]
    (Files/walkFileTree
      src-path
      (u.jio/make-file-visitor
        (fn [[path attr]]
          (let [entry-name (str (.relativize src-path path))]
            (io/copy! path (doto (.resolve dest-path entry-name) (u.jio/mkparents)))))))))


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
  - coords: A map with the same format than tools.deps maven coordinates.
  - out-path: The path of the produced jar file. When not provided, a default out-path is generated from the lib and maven coordinates.
  - main: A namespace to be added to the \"Main\" entry to the jar manifest. Default to nil.
  - manifest: A map of additionel entries to the jar manifest. Values of the manifest map can be maps to represent manifest sections. By default, the jar manifest contains the \"Created-by\", \"Built-By\" and \"Build-Jdk\" entries.
  - deps: The dependencies of the project. deps have the same format than the :deps entry of a tools.deps map. Dependencies are copied to the pom.xml file produced while generating the jar file. Default to the deps.edn dependencies of the project (excluding the system-level and user-level deps.edn dependencies).
  - mvn/repos: Repositories to be copied to the pom.xml file produced while generating the jar. Must have same format than the :mvn/repos entry of deps.edn. Default to nil.
  - exclusion-predicates: Predicates to exclude operations that would otherwise been operated to the jar. Each predicate takes a parameter: file-operation. Default to predicates that excludes dotfiles and emacs backup files.
  - allow-all-dependencies?: A boolean that can be set to true to allow any types of dependency, such as local or git dependencies. Default to false, in which case only maven dependencies are allowed - an exception is thrown when this is not the case. When set to true, the jar is produced even in the presence of non-maven dependencies, but only maven dependencies are added to the jar."
  ([lib coords]
   (jar lib coords nil nil))
  ([lib coords
    paths
    {:keys [out-path
            target-dir
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
     :as   _options}]
   (let [[lib coords]   (if (and pom-path (u.jio/file? pom-path))
                                (let [pom (pom/read-pom pom-path)]
                                  [(or lib (maven/get-library-from-pom pom))
                                   (update coords :mvn/version #(or % (.getVersion pom)))])
                                [lib coords])
         lib                  (if (qualified-symbol? lib) lib (symbol (name lib) (name lib)))
         artifact-id          (name lib)
         group-id             (namespace lib)
         target-dir          (u.jio/path (or target-dir "target"))
         out-path             (u.jio/path (or out-path
                                              (and jarname (u.jio/path-resolve target-dir jarname))
                                              (make-jarpath artifact-id coords target-dir)))
         _                    (when-not (str/ends-with? (str out-path) ".jar")
                                (throw
                                  (ex-info "out-path must be a jar file"
                                    {:out-path out-path})))
         deps-map             (or deps-map (u.deps/project-deps))
         paths                (or paths (:paths deps-map))
         exclusion-predicates (or exclusion-predicates [io/dotfiles-exclusion-predicate
                                                        io/emacs-backups-exclusion-predicate])]
     (when-not allow-all-dependencies?
       (check-non-maven-dependencies deps-map))
     (with-open [jarfs (util.jar/getjarfs out-path)]
       (let [the-manifest   (util.jar/create-manifest main manifest)
             pom-properties (or pom-properties (pom/make-pom-properties lib coords))]
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


(defn jar-x
  [{:keys [lib coords paths
           out-path
           target-dir
           jarname
           compile-path
           deps-map
           main
           manifest
           pom-path
           pom-properties
           extra-operations
           exclusion-predicates
           allow-all-dependencies?]}]
  (jar lib coords paths
       {:out-path                out-path
        :target-dir             target-dir
        :jarname                 jarname
        :compile-path            compile-path
        :deps-map                deps-map
        :main                    main
        :manifest                manifest
        :pom-path                pom-path
        :pom-properties          pom-properties
        :extra-operations        extra-operations
        :exclusion-predicates    exclusion-predicates
        :allow-all-dependencies? allow-all-dependencies?}))


(defn uberjar
  [jarpath classpath]
  (let [classpath (or classpath (u.deps/make-classpath))]
    (with-open [jarfs (util.jar/getjarfs jarpath)]
      (uber-classpath classpath (u.jio/path jarfs))))
  jarpath)


(defn uberjar-x
  [{:keys [lib coords paths
           out-path
           target-dir
           jarname
           compile-path
           deps-map
           main
           manifest
           pom-path
           pom-properties
           extra-operations
           exclusion-predicates
           allow-all-dependencies?
           classpath]}]
  (uberjar
    (jar lib coords paths
         {:out-path                out-path
          :target-dir              target-dir
          :jarname                 jarname
          :compile-path            compile-path
          :deps-map                deps-map
          :main                    main
          :manifest                manifest
          :pom-path                pom-path
          :pom-properties          pom-properties
          :extra-operations        extra-operations
          :exclusion-predicates    exclusion-predicates
          :allow-all-dependencies? allow-all-dependencies?})
    classpath))


(defn uber-x
  [{:keys [jarpath classpath]}]
  (uberjar jarpath classpath))


(set! *warn-on-reflection* false)


(comment
  (jar 'user.tools.deps.alpha {:mvn/version "0.0.0"} nil {})
  )
