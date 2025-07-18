(ns i2p-clj.router
  (:require
   [taoensso.timbre :refer [info]])
  (:import
   (java.io File)
   (java.util Properties)
   (net.i2p.router Router)))

(defn ^Router create-router [& {:keys [config]}]
  (let [r (if config
            (new Router config)
            (new Router))]
    (.setKillVMOnEnd r false)
    (.runRouter r)
    (info "Waiting for router to start...")
    (while (not (.isRunning r))
      (Thread/sleep 1000))
    (info "Router started.")
    r))

(defn shutdown-router
  "shutdown-router blocks. May take several seconds for
  java hooks to complete
  NOTE: for non-blocking use gracefulShutdown
  TODO: provide option for graceful non-blocking shutdown"
  [^Router router & {:keys [code]
                     :or   {code :graceful}}]
  (let [code-number (case code
                      ;; codes found in Router.java
                      :graceful         2
                      :hard             3
                      :oom              10
                      :hard-restart     4
                      :graceful-restart 5
                      (throw (ex-info "Invalid exit code provided"
                                      {:type        :validatation-error
                                       :field       :code
                                       :value       code
                                       :constraints #{2 3 10 4 5}}
                                      (IllegalArgumentException. "Exit code must be: 2, 3, 4, 5, or 10"))))]
    (when (not (nil? router))
      (info "Shutting down router...")
      (.shutdown router (int code-number))
      (info "Router is now shutdown"))))

(defn create-router-config
  "Create comprehensive I2P router configuration Properties object.

  This configures the I2P router itself (not client connections).
  Based on router.config file format and documented I2P router properties.

  Example usage:
  (create-router-config {:router-dir \"/home/user/.i2p\"
                         :i2np-ntcp-port 8887
                         :bandwidth-share-percentage 90})
  (create-router-config {:laptop-mode true :max-participating-tunnels 200})"
  [& {:keys [;; Directory configuration
             router-dir i2p-dir-base i2p-dir-config i2p-dir-router
             i2p-dir-log i2p-dir-app i2p-dir-pid

             ;; Network ports
             i2np-ntcp-port i2np-udp-port i2cp-port admin-port
             routerconsole-port

             ;; Bandwidth settings
             i2np-bandwidth-inbound-kb-per-sec i2np-bandwidth-outbound-kb-per-sec
             i2np-bandwidth-inbound-burst-kb i2np-bandwidth-outbound-burst-kb
             i2np-bandwidth-inbound-burst-kb-per-sec i2np-bandwidth-outbound-burst-kb-per-sec

             ;; Transport settings
             i2np-ntcp-enable i2np-ntcp-autoip i2np-udp-enable
             i2np-udp-address-sources i2np-upnp-enable i2np-laptop-mode

             ;; Router behavior
             router-dynamic-keys router-max-participating-tunnels
             router-share-percentage router-news-refresh-frequency
             router-update-policy router-update-through-proxy
             router-update-proxy-host router-update-proxy-port
             router-update-url router-update-unsigned

             ;; Console settings
             routerconsole-lang routerconsole-summary-refresh
             routerconsole-theme

             ;; Time and security
             time-disabled prng-buffers

             ;; Advanced settings
             i2cp-tcp-bind-all-interfaces
             router-floodfill-participant
             stat-manager-frequency]
      :or {;; Sensible defaults based on I2P documentation
           ;; Network ports (auto-selected if not specified)
           i2cp-port 7654
           routerconsole-port 7657

           ;; Bandwidth (conservative defaults - 128 KB/s)
           i2np-bandwidth-inbound-kb-per-sec 128
           i2np-bandwidth-outbound-kb-per-sec 128
           i2np-bandwidth-inbound-burst-kb 22520
           i2np-bandwidth-outbound-burst-kb 22520
           i2np-bandwidth-inbound-burst-kb-per-sec 128
           i2np-bandwidth-outbound-burst-kb-per-sec 128

           ;; Transport settings
           i2np-ntcp-enable true
           i2np-ntcp-autoip true
           i2np-udp-enable true
           i2np-udp-address-sources "local,upnp,ssu"
           i2np-upnp-enable true
           i2np-laptop-mode false

           ;; Router behavior
           router-dynamic-keys false
           router-max-participating-tunnels 150
           router-share-percentage 80
           router-news-refresh-frequency 86400000
           router-update-policy "notify"
           router-update-through-proxy true
           router-update-proxy-host "127.0.0.1"
           router-update-proxy-port 4444
           router-update-unsigned false

           ;; Console settings
           routerconsole-lang "en"
           routerconsole-summary-refresh 30

           ;; Time and security
           time-disabled false
           prng-buffers 16

           ;; Advanced settings
           i2cp-tcp-bind-all-interfaces false
           router-floodfill-participant false}}]
  (let [props (java.util.Properties.)
        base-dir (or router-dir i2p-dir-base)]

    ;; Directory configuration
    (when base-dir
      (.setProperty props "i2p.dir.base" (.getAbsolutePath (File. base-dir))))
    (when i2p-dir-config
      (.setProperty props "i2p.dir.config" (.getAbsolutePath (File. i2p-dir-config))))
    (when i2p-dir-router
      (.setProperty props "i2p.dir.router" (.getAbsolutePath (File. i2p-dir-router))))
    (when i2p-dir-log
      (.setProperty props "i2p.dir.log" (.getAbsolutePath (File. i2p-dir-log))))
    (when i2p-dir-app
      (.setProperty props "i2p.dir.app" (.getAbsolutePath (File. i2p-dir-app))))
    (when i2p-dir-pid
      (.setProperty props "i2p.dir.pid" (.getAbsolutePath (File. i2p-dir-pid))))

    ;; Use subdirectories if base-dir is specified but specific dirs aren't
    (when (and base-dir (not i2p-dir-config))
      (.setProperty props "i2p.dir.config" (.getAbsolutePath (File. base-dir "config"))))
    (when (and base-dir (not i2p-dir-router))
      (.setProperty props "i2p.dir.router" (.getAbsolutePath (File. base-dir "router"))))
    (when (and base-dir (not i2p-dir-log))
      (.setProperty props "i2p.dir.log" (.getAbsolutePath (File. base-dir "log"))))
    (when (and base-dir (not i2p-dir-app))
      (.setProperty props "i2p.dir.app" (.getAbsolutePath (File. base-dir "app"))))

    ;; Network ports
    (when i2np-ntcp-port (.setProperty props "i2np.ntcp.port" (str i2np-ntcp-port)))
    (when i2np-udp-port (.setProperty props "i2np.udp.port" (str i2np-udp-port)))
    (.setProperty props "i2cp.port" (str i2cp-port))
    (when admin-port (.setProperty props "router.adminPort" (str admin-port)))
    (.setProperty props "routerconsole.port" (str routerconsole-port))

    ;; Bandwidth settings
    (.setProperty props "i2np.bandwidth.inboundKBytesPerSecond" (str i2np-bandwidth-inbound-kb-per-sec))
    (.setProperty props "i2np.bandwidth.outboundKBytesPerSecond" (str i2np-bandwidth-outbound-kb-per-sec))
    (.setProperty props "i2np.bandwidth.inboundBurstKBytes" (str i2np-bandwidth-inbound-burst-kb))
    (.setProperty props "i2np.bandwidth.outboundBurstKBytes" (str i2np-bandwidth-outbound-burst-kb))
    (.setProperty props "i2np.bandwidth.inboundBurstKBytesPerSecond" (str i2np-bandwidth-inbound-burst-kb-per-sec))
    (.setProperty props "i2np.bandwidth.outboundBurstKBytesPerSecond" (str i2np-bandwidth-outbound-burst-kb-per-sec))

    ;; Transport settings
    (.setProperty props "i2np.ntcp.enable" (str i2np-ntcp-enable))
    (.setProperty props "i2np.ntcp.autoip" (str i2np-ntcp-autoip))
    (.setProperty props "i2np.udp.enable" (str i2np-udp-enable))
    (.setProperty props "i2np.udp.addressSources" i2np-udp-address-sources)
    (.setProperty props "i2np.upnp.enable" (str i2np-upnp-enable))
    (.setProperty props "i2np.laptopMode" (str i2np-laptop-mode))

    ;; Router behavior
    (.setProperty props "router.dynamicKeys" (str router-dynamic-keys))
    (.setProperty props "router.maxParticipatingTunnels" (str router-max-participating-tunnels))
    (.setProperty props "router.sharePercentage" (str router-share-percentage))
    (.setProperty props "router.newsRefreshFrequency" (str router-news-refresh-frequency))
    (.setProperty props "router.updatePolicy" router-update-policy)
    (.setProperty props "router.updateThroughProxy" (str router-update-through-proxy))
    (.setProperty props "router.updateProxyHost" router-update-proxy-host)
    (.setProperty props "router.updateProxyPort" (str router-update-proxy-port))
    (.setProperty props "router.updateUnsigned" (str router-update-unsigned))

    ;; Console settings
    (.setProperty props "routerconsole.lang" routerconsole-lang)
    (.setProperty props "routerconsole.summaryRefresh" (str routerconsole-summary-refresh))
    (when routerconsole-theme (.setProperty props "routerconsole.theme" routerconsole-theme))

    ;; Time and security
    (.setProperty props "time.disabled" (str time-disabled))
    (.setProperty props "prng.buffers" (str prng-buffers))

    ;; Advanced settings
    (.setProperty props "i2cp.tcp.bindAllInterfaces" (str i2cp-tcp-bind-all-interfaces))
    (when router-floodfill-participant
      (.setProperty props "router.floodfillParticipant" (str router-floodfill-participant)))
    (when stat-manager-frequency
      (.setProperty props "stat.managerFrequency" (str stat-manager-frequency)))

    ;; Optional string properties
    (when router-update-url (.setProperty props "router.updateURL" router-update-url))

    props))
