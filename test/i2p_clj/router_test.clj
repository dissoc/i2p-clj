(ns i2p-clj.router-test
  (:require
   [clojure.test :refer :all]
   [i2p-clj.router :refer :all])
  (:import
   (java.util Properties)))

(deftest create-router-and-shutdown
  (testing "creating router"
    (let [router (create-router)]
      (is (.isAlive router))

      (testing "shutting down router (blocking)"
        (shutdown-router router)
        (is (not (.isAlive router)))))))

(deftest create-router-config-test
  (testing "create-router-config with defaults"
    (let [props (create-router-config)]
      (is (instance? Properties props))
      ;; Test default values
      (is (= "7654" (.getProperty props "i2cp.port")))
      (is (= "7657" (.getProperty props "routerconsole.port")))
      (is (= "128" (.getProperty props "i2np.bandwidth.inboundKBytesPerSecond")))
      (is (= "128" (.getProperty props "i2np.bandwidth.outboundKBytesPerSecond")))
      (is (= "true" (.getProperty props "i2np.ntcp.enable")))
      (is (= "true" (.getProperty props "i2np.upnp.enable")))
      (is (= "false" (.getProperty props "i2np.laptopMode")))
      (is (= "150" (.getProperty props "router.maxParticipatingTunnels")))
      (is (= "80" (.getProperty props "router.sharePercentage")))
      (is (= "notify" (.getProperty props "router.updatePolicy")))
      (is (= "en" (.getProperty props "routerconsole.lang")))
      (is (= "false" (.getProperty props "time.disabled")))))

  (testing "create-router-config with custom values"
    (let [props (create-router-config
                 :i2cp-port 7655
                 :routerconsole-port 7658
                 :i2np-bandwidth-inbound-kb-per-sec 512
                 :router-max-participating-tunnels 300
                 :i2np-laptop-mode true)]
      (is (= "7655" (.getProperty props "i2cp.port")))
      (is (= "7658" (.getProperty props "routerconsole.port")))
      (is (= "512" (.getProperty props "i2np.bandwidth.inboundKBytesPerSecond")))
      (is (= "300" (.getProperty props "router.maxParticipatingTunnels")))
      (is (= "true" (.getProperty props "i2np.laptopMode")))))

  (testing "create-router-config with directory configuration"
    (let [test-dir "/tmp/i2p-test"
          props (create-router-config :router-dir test-dir)]
      (is (= test-dir (.getProperty props "i2p.dir.base")))
      ;; Should create subdirectories automatically
      (is (.endsWith (.getProperty props "i2p.dir.config") "config"))
      (is (.endsWith (.getProperty props "i2p.dir.router") "router"))
      (is (.endsWith (.getProperty props "i2p.dir.log") "log"))
      (is (.endsWith (.getProperty props "i2p.dir.app") "app"))))

  (testing "create-router-config with specific directory paths"
    (let [props (create-router-config
                 :i2p-dir-base "/opt/i2p"
                 :i2p-dir-config "/opt/i2p/custom-config"
                 :i2p-dir-log "/var/log/i2p")]
      (is (= "/opt/i2p" (.getProperty props "i2p.dir.base")))
      (is (= "/opt/i2p/custom-config" (.getProperty props "i2p.dir.config")))
      (is (= "/var/log/i2p" (.getProperty props "i2p.dir.log")))))

  (testing "create-router-config with optional string properties"
    (let [props (create-router-config
                 :routerconsole-theme "dark"
                 :router-update-url "http://custom.update.server/i2pupdate.sud")]
      (is (= "dark" (.getProperty props "routerconsole.theme")))
      (is (= "http://custom.update.server/i2pupdate.sud" (.getProperty props "router.updateURL"))))))
