;;; Copyright © 2025 Justin Bishop
;;; (apply str (reverse '("me" "." "dissoc" "@" "mail")))

(defproject i2p-clj "0.1.0"
  :description "clojure wrapper for i2p network providing server and client"
  :url "https://github.com/dissoc/i2p-clj"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :java-source-paths ["~/src/narayana/"]
  :target-path "target/"
  :dependencies [[clj-commons/fs "1.6.307"]
                 [clojure.java-time "1.4.3"]
                 [com.cognitect/transit-clj "1.0.333"]
                 [com.taoensso/timbre "6.7.0"]
                 [cprop "0.1.20"]
                 [mount "0.1.20"]
                 [net.i2p.client/mstreaming "2.7.0"]
                 [net.i2p.client/streaming "2.7.0"]
                 [net.i2p/i2p "2.7.0"]
                 [net.i2p/router "2.7.0"]
                 [ch.qos.logback/logback-classic "1.5.18"]
                 ;;[org.apache.activemq/apache-artemis "2.38.0" :extension "pom"]
                 ;;[org.cloboss/messaging "2.2.0-SNAPSHOT"]
                 [org.clojure/clojure "1.11.1"]
                 [org.jboss.logging/jboss-logging "3.6.1.Final"]
                 [org.jboss.narayana.jta/narayana-jta "7.1.0.Final"
                  :exclusions [org.jboss.logging/jboss-logging]]]
  :repositories [["github"
                  {:url      "https://maven.pkg.github.com/dissoc/i2p-clj"
                   :username :env/github_username
                   :password :env/github_token
                   :sign-releases true}]]
  :deploy-repositories [["github"
                         {:url      "https://maven.pkg.github.com/dissoc/i2p-clj"
                          :username :env/github_username
                          :password :env/github_token
                          :sign-releases true}]]
  :signing {:gpg-key :env/gpg_key_id}
  :aot [i2p-clj.i2p-xaresource i2p-clj.core]
  :main [i2p-clj.core]
  :jvm-opts ["-Dcom.arjuna.ats.arjuna.loggerLevel=DEBUG" "-Djdk.attach.allowAttachSelf=true"]
  :test-paths ["test"]
  :repl-options {:init-ns i2p-clj.core}
  :resource-paths ["resources"]
  :profiles
  {:test ;;[:project/test :project/test]
   {:jvm-opts       ["-Dconf=test-config.edn"]
    :resource-paths ["env/test/resources"]}
   :dev ;;[:project/test :project/test]
   {:jvm-opts       ["-Dconf=dev-config.edn"]
    :resource-paths ["env/dev/resources"]}})
