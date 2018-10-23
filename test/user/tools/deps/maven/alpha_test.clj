(ns user.tools.deps.maven.alpha-test
  (:require
   [clojure.test :as test :refer [deftest is are testing]]
   [user.tools.deps.maven.alpha :refer :all]
   ))


;; https://github.com/sonatype/plexus-sec-dispatcher/blob/master/src/main/java/org/sonatype/plexus/components/sec/dispatcher/DefaultSecDispatcher.java#L192


(comment
  (let [security-dispatcher       (doto (DefaultSecDispatcher.) (.setConfigurationFile (str (jio/file (System/getProperty "user.home") ".m2" "settings-security.xml"))))
        settings-decrypter        (DefaultSettingsDecrypter.)
        security-dispatcher-field (.getDeclaredField DefaultSettingsDecrypter "securityDispatcher")
        _                         (.setAccessible security-dispatcher-field true)
        _                         (.set security-dispatcher-field settings-decrypter security-dispatcher)
        settings                  (get-settings)
        decrypt-request           (DefaultSettingsDecryptionRequest. settings)]
    (.decrypt settings-decrypter decrypt-request)
    settings)


  (.decrypt (DefaultSettingsDecrypter.) (DefaultSettingsDecryptionRequest. (get-settings)))


  (class (.getAuthentication (util.maven/remote-repo ["clojars" {:url "https://clojars.org/repo"}])))


  DefaultSecDispatcher/ATTR_START


  (.getConfigurationFile (DefaultSecDispatcher.))


  (.decrypt
    (doto (DefaultSecDispatcher.) (.setConfigurationFile (str (jio/file (System/getProperty "user.home") ".m2" "settings-security.xml"))))
    "L3OJBK1eJIwHCXK/Ld0TuRS8uEkro8+hzydzCqU/Q5Yf6Z7zZ0kdcububTlvxTKutFMRBgevWjXvdZDeWnOI8A=="
    )
  )
