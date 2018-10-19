(ns user.tools.deps.util.jar
  (:require
   [clojure.java.io :as jio]
   [clojure.string :as str]
   [user.java.io.alpha :as io]
   )
  (:import
   java.io.ByteArrayOutputStream
   java.io.OutputStream
   java.net.URI
   java.nio.file.FileSystem
   java.nio.file.FileSystems
   java.nio.file.FileSystemLoopException
   java.nio.file.FileVisitOption
   java.nio.file.FileVisitResult
   java.nio.file.FileVisitor
   java.nio.file.Files
   java.nio.file.NoSuchFileException
   java.nio.file.Path
   java.nio.file.Paths
   java.nio.file.attribute.BasicFileAttributes
   java.nio.file.attribute.FileAttribute
   java.util.EnumSet
   java.util.HashMap
   java.util.Map
   java.util.Properties
   java.util.jar.Attributes
   java.util.jar.Attributes$Name
   java.util.jar.JarEntry
   java.util.jar.JarOutputStream
   java.util.jar.Manifest
   ))


(set! *warn-on-reflection* true)


;; * jarfs utils


(defn mkjaruri
  ^URI
  [jarpath]
  (URI/create (str "jar:" (.. (io/as-path jarpath) toAbsolutePath normalize toUri))))


(defn mkjarfs
  ^FileSystem
  ([jarpath]
   (mkjarfs jarpath nil))
  ([jarpath {:keys [create encoding]}]
   (let [jaruri (mkjaruri jarpath)
         env    (HashMap.)]
     (when create
       (io/mkparents jarpath)
       (.put env "create" (str (boolean create))))
     (when encoding (.put env "encoding" (str encoding)))
     (FileSystems/newFileSystem jaruri env))))


(defn getjarfs
  ^FileSystem
  [jarpath]
  (let [jaruri (mkjaruri jarpath)]
    (if (io/file? jarpath)
      (try
        (FileSystems/getFileSystem jaruri)
        (catch java.nio.file.FileSystemNotFoundException _
          (mkjarfs jarpath)))
      (mkjarfs jarpath {:create true}))))


;; * manifest


(def ^:private default-manifest
  {"Built-By"   (System/getProperty "user.name")
   "Build-Jdk"  (System/getProperty "java.version")
   "Created-By" "user.tools.deps.alpha"})


(defn- put-attributes
  [^Attributes attributes ^java.util.Map kvs]
  (doseq [[k v] kvs]
    (.put attributes (Attributes$Name. (name k)) (str v))))


(defn create-manifest
  ^Manifest
  [main ext-attrs]
  (let [manifest   (Manifest.)
        attributes (.getMainAttributes manifest)]
    (.put attributes Attributes$Name/MANIFEST_VERSION "1.0")
    (when-let [main (and main (munge (str main)))]
      (.put attributes Attributes$Name/MAIN_CLASS main))
    (let [{main-attrs false sections true} (group-by (fn [e] (coll? (val e))) (merge default-manifest ext-attrs))]
      (put-attributes attributes main-attrs)
      (let [^Map entries (. manifest getEntries)]
        (doseq [[n kvs] sections
                :let    [attributes (Attributes.)]]
          (put-attributes attributes kvs)
          (.put entries (name n) attributes))))
    manifest))


(defn get-manifest-txt
  [^Manifest manifest]
  (with-open [os (ByteArrayOutputStream.)]
    (. manifest write os)
    (. os toString)))


(set! *warn-on-reflection* false)


(comment
  (paths-copy-operations ["src" "test"])
  )
