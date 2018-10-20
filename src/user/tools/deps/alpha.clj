(ns user.tools.deps.alpha
  (:require
   [clojure.string :as str]
   [clojure.java.io :as jio]
   [clojure.tools.deps.alpha :as deps]
   [clojure.tools.deps.alpha.reader :as deps.reader]
   [clojure.tools.deps.alpha.script.make-classpath :as deps.make-classpath]
   ))


(set! *warn-on-reflection* true)


(defn deps-map
  []
  (deps.reader/read-deps (:config-files (deps.reader/clojure-env))))


(defn make-classpath
  ([]
   (make-classpath nil))
  ([opts]
   (make-classpath (deps-map) opts))
  ([deps-map {:keys [resolve-aliases makecp-aliases aliases] :as opts}]
   (:classpath (deps.make-classpath/create-classpath deps-map opts))))


(defn get-jarpath
  ^String
  [lib coord]
  (let [path (get-in (deps/resolve-deps {:deps {lib coord}} {}) [lib :paths 0])]
    (when (str/ends-with? path ".jar")
      path)))


(set! *warn-on-reflection* false)
