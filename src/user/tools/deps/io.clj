(ns user.tools.deps.io
  (:require
   [clojure.string :as str]
   [clojure.java.io :as jio]
   [clojure.edn :as edn]
   [user.java.io.alpha :as u.jio]
   )
  (:import
   java.io.InputStream
   java.io.PushbackReader
   java.nio.file.Path
   java.nio.file.Files
   java.nio.file.attribute.BasicFileAttributes
   java.util.jar.JarFile
   java.util.jar.JarEntry
   java.util.jar.JarInputStream
   ))


;; * clash


(def ^:dynamic *clash-verbose* false)


(defn clash-strategy
  [target]
  (let [^String entry-name (str target)]
    (cond
      (contains? #{"data_readers.clj"} entry-name)       :merge-edn
      (str/starts-with? entry-name "META-INF/services/") :concat-lines
      :else                                              :noop)))


(defmulti clash
  {:arglists '([src target])}
  (fn [_ target]
    (clash-strategy target)))


(defmethod clash :default
  [src target]
  (when *clash-verbose*
    (println "Clash appeared, do nothing -" (str target)))
  ;; do nothing special, first entry wins
  )


(defmethod clash :merge-edn
  [src target]
  (let [er   #(edn/read (PushbackReader. %))
        r1   (jio/reader src)
        r2   (jio/reader target)
        edn1 (er r1)
        edn2 (er r2)]
    (u.jio/copy!
      (jio/input-stream (.getBytes (pr-str (merge edn1 edn2))))
      target)))


(defn- ensure-newline
  ^String
  [^String s]
  (if (str/ends-with? s "\n")
    s
    (str s "\n")))


(defmethod clash :concat-lines
  [src target]
  (let [f1 (line-seq (jio/reader src))
        f2 (Files/readAllLines target)]
    (u.jio/copy!
      (jio/input-stream (.getBytes (ensure-newline (str/join "\n" (concat f2 f1)))))
      target)))


;;


(defn- input-stream-or-as-path
  [x]
  (if (instance? InputStream x)
    x
    (u.jio/as-path x)))


(defn copy!
  [src target]
  (if (u.jio/exists? target)
    (clash src target)
    (u.jio/copy! (input-stream-or-as-path src) (doto target (u.jio/mkparents)))))


;;


(defn consume-jar
  [jarpath consume]
  (with-open [is (JarInputStream. (jio/input-stream (u.jio/path jarpath)))]
    (loop []
      (when-let [entry (.getNextJarEntry is)]
        (consume is entry)
        (recur)))))


;; * batch operation


;; ** predicate


(defn dotfiles-exclusion-predicate
  [{:keys [path]}]
  (let [path (u.jio/path path)]
    (.startsWith path ".")))


(defn emacs-backups-exclusion-predicate
  [{:keys [path]}]
  (let [path (u.jio/path path)]
    (or (.endsWith path "~")
        (.startsWith path "#"))))


;; ** do operation


(defn- do-copy-operation
  [^Path dest-path {:keys [src path] :as operation}]
  (let [target (u.jio/path-resolve dest-path path)]
    (copy! src target)))


(defn- do-write-operation
  [^Path dest-path {:keys [path write-fn] :as operation}]
  (u.jio/write! (doto (u.jio/path-resolve dest-path path) (u.jio/mkparents)) write-fn))


(defn do-operations
  [^Path dest-path operations]
  (run!
    (fn [operation]
      (try
        (case (:op operation)
          (:copy :copy!)   (do-copy-operation dest-path operation)
          (:write :write!) (do-write-operation dest-path operation)
          (throw (UnsupportedOperationException. (pr-str operation))))
        (catch Throwable e
          (throw (ex-info "Operation failed:" {:operation operation :exception e})))))
    operations))


;; ** operation sift fns


(defn sift-add-paths
  ([operations paths]
   (sift-add-paths operations (constantly true) paths))
  ([operations predicate paths]
   (let [tcoll (transient operations)]
     (run!
       (fn [path]
         (when (u.jio/directory? path)
           (u.jio/transduce-file-tree-1
             (comp
               (filter predicate)
               (map
                 (fn [[^Path path' ^BasicFileAttributes attrs]]
                   {:op   :copy
                    :src  path'
                    :path (.relativize (u.jio/as-path path) path')
                    :time (. attrs lastModifiedTime)})))
             (fn
               ([_])
               ([tcoll op] (conj! tcoll op)))
             tcoll
             path)))
       paths)
     (persistent! tcoll))))


(defn sift-add-jar
  [operations ^String jarpath re]
  (let [jarfile (JarFile. jarpath)]
    (transduce
      (comp
        (filter (fn [^JarEntry entry] (re-matches re (.getName entry))))
        (map
          (fn [^JarEntry entry]
            {:op   :copy
             :src  (.getInputStream jarfile entry)
             :path (.getName entry)
             :time (. entry getLastModifiedTime)})))
      conj
      (or operations [])
      (enumeration-seq (.entries jarfile)))))


(defn sift-move
  [operations match replacement]
  (into []
    (map
      (fn [operation]
        (-> operation
          (update :path str)
          (update :path str/replace match replacement))))
    operations))
