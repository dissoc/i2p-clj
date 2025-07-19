;;; Copyright © 2025 Justin Bishop
;;; (apply str (reverse '("me" "." "dissoc" "@" "mail")))

(defproject io.github.dissoc/i2p-clj "0.1.1-SNAPSHOT"
  :description "A Clojure library providing I2P anonymous network integration with socket management and router configuration"
  :url "https://github.com/dissoc/i2p-clj"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :target-path "target/"
  :dependencies [[ch.qos.logback/logback-classic "1.5.18"]
                 [com.cognitect/transit-clj "1.0.333"]
                 [com.taoensso/timbre "6.7.0"]
                 [net.i2p/i2p "2.7.0"]
                 [net.i2p/router "2.7.0"]
                 [net.i2p.client/mstreaming "2.7.0"]
                 [net.i2p.client/streaming "2.7.0"]
                 [org.clojure/clojure "1.11.1"]]
  :repositories [["github"
                  {:url           "https://maven.pkg.github.com/dissoc/i2p-clj"
                   :username      ~(System/getenv "GITHUB_USERNAME")
                   :password      ~(System/getenv "GITHUB_TOKEN")
                   :sign-releases false}]]
  :deploy-repositories [["github"
                         {:url           "https://maven.pkg.github.com/dissoc/i2p-clj"
                          :username      ~(System/getenv "GITHUB_USERNAME")
                          :password      ~(System/getenv "GITHUB_TOKEN")
                          :sign-releases true}]]
  :signing {:gpg-key        ~(System/getenv "GPG_KEY_ID")
            :gpg-passphrase ~(System/getenv "LEIN_GPG_PASSPHRASE")}
  :aot [i2p-clj.core]
  :jvm-opts ["-Djdk.attach.allowAttachSelf=true"]
  :test-paths ["test"]
  :repl-options {:init-ns i2p-clj.core}
  :resource-paths ["resources"]
  :profiles
  {:test
   {:jvm-opts       ["-Dconf=test-config.edn"]
    :resource-paths ["env/test/resources"]}
   :dev
   {:plugins        [[cider/cider-nrepl "0.57.0"]]
    :repl-options   {:port 7888}
    :jvm-opts       ["-Dconf=dev-config.edn"]
    :resource-paths ["env/dev/resources"]}})
