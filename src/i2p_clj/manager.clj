(ns i2p-clj.manager
  (:require
   [i2p-clj.util :refer [b32->input-stream]])
  (:import
   (java.util Properties)
   (net.i2p.client.streaming I2PSocketManager I2PSocketManagerFactory IncomingConnectionFilter)))

;; NOTE: there are two different functions to create options
;; create-router-side-options: when using the manager on the router side
;; create-client-side-options: when using the manager on the client side
;;
;; some parameters will be ignored when used on the incorrect side

(defn- ^Boolean create-connection-filter
  "Filter returns boolean with input of destination
  Here you check to allow -> true or to deny -> false"
  [check-destination-fn]
  (reify IncomingConnectionFilter
    (allowDestination [this destination]
      (let [address-base64 (.toBase64 destination)]
        (check-destination-fn address-base64)))))

(defn ^I2PSocketManager create-socket-manager
  "Create an I2P socket manager

  Parameters:
  - destination-key: Base32 string, InputStream, or nil
  - connection-filter: Function to filter incoming connections or nil
  - host: Router host (default localhost)
  - port: Router port (default 7654)
  - options: Properties map or java.util.Properties object

  Examples:
  (create-socket-manager :destination-key \"mykey.b32\" :connection-filter my-filter)
  (create-socket-manager :host \"192.168.1.100\" :port 7655)
  (create-socket-manager :options {:inbound-length 3})"
  [& {:keys [destination-key connection-filter host port options]
      :or {host "localhost" port 7654}}]
  (let [key-stream (when destination-key
                     (if (instance? java.io.InputStream destination-key)
                       destination-key
                       (b32->input-stream destination-key)))
        props (cond
                (instance? java.util.Properties options) options
                (map? options) (let [p (java.util.Properties.)]
                                 (doseq [[k v] options]
                                   (.setProperty p (name k) (str v)))
                                 p)
                :else (java.util.Properties.))
        filter (when connection-filter
                 (create-connection-filter connection-filter))]

    ;; Use the most complete Java method signature and let Java handle nulls
    (if key-stream
      ;; Has key stream - use the 5-argument version
      (I2PSocketManagerFactory/createManager key-stream host (int port) props filter)
      ;; No key stream - use the 4-argument version
      (I2PSocketManagerFactory/createManager host (int port) props filter))))

(defn ^Properties create-router-side-options
  "Create router-side I2CP options Properties object.

  Router-side options control tunnel behavior and are passed to the router
  via SessionConfig in CreateSession or ReconfigureSession messages.

  Documentation: https://geti2p.net/en/docs/protocol/i2cp#options

  Example usage:
  (create-router-side-options :inbound-length 4 :outbound-quantity 3)
  (create-router-side-options :crypto-tags-to-send 60 :should-bundle-reply-info false)"
  [& {:keys [;; Basic tunnel configuration
             inbound-length outbound-length
             inbound-quantity outbound-quantity
             inbound-backup-quantity outbound-backup-quantity
             inbound-length-variance outbound-length-variance
             inbound-nickname outbound-nickname
             inbound-allow-zero-hop outbound-allow-zero-hop
             inbound-ip-restriction outbound-ip-restriction
             inbound-random-key outbound-random-key
             outbound-priority

             ;; Cryptography options
             crypto-tags-to-send
             crypto-low-tag-threshold
             crypto-ratchet-inbound-tags
             crypto-ratchet-outbound-tags

             ;; Advanced router options
             client-message-timeout
             should-bundle-reply-info
             explicit-peers
             i2cp-dont-publish-lease-set
             i2cp-fast-receive]
      :or {;; Defaults matching I2P documentation
           inbound-length           3
           outbound-length          3
           inbound-quantity         2
           outbound-quantity        2
           inbound-backup-quantity  0
           outbound-backup-quantity 0
           inbound-length-variance  0
           outbound-length-variance 0
           inbound-allow-zero-hop   true
           outbound-allow-zero-hop  true
           inbound-ip-restriction   2
           outbound-ip-restriction  2
           outbound-priority        0

           crypto-tags-to-send          40
           crypto-low-tag-threshold     30
           crypto-ratchet-inbound-tags  160
           crypto-ratchet-outbound-tags 160

           client-message-timeout      60000
           should-bundle-reply-info    true
           i2cp-dont-publish-lease-set false
           i2cp-fast-receive           false}}]
  (let [props (java.util.Properties.)]
    ;; Basic tunnel configuration
    (.setProperty props "inbound.length" (str inbound-length))
    (.setProperty props "outbound.length" (str outbound-length))
    (.setProperty props "inbound.quantity" (str inbound-quantity))
    (.setProperty props "outbound.quantity" (str outbound-quantity))
    (.setProperty props "inbound.backupQuantity" (str inbound-backup-quantity))
    (.setProperty props "outbound.backupQuantity" (str outbound-backup-quantity))
    (.setProperty props "inbound.lengthVariance" (str inbound-length-variance))
    (.setProperty props "outbound.lengthVariance" (str outbound-length-variance))
    (.setProperty props "inbound.allowZeroHop" (str inbound-allow-zero-hop))
    (.setProperty props "outbound.allowZeroHop" (str outbound-allow-zero-hop))
    (.setProperty props "inbound.IPRestriction" (str inbound-ip-restriction))
    (.setProperty props "outbound.IPRestriction" (str outbound-ip-restriction))
    (.setProperty props "outbound.priority" (str outbound-priority))

    ;; Cryptography options
    (.setProperty props "crypto.tagsToSend" (str crypto-tags-to-send))
    (.setProperty props "crypto.lowTagThreshold" (str crypto-low-tag-threshold))
    (.setProperty props "crypto.ratchet.inboundTags" (str crypto-ratchet-inbound-tags))
    (.setProperty props "crypto.ratchet.outboundTags" (str crypto-ratchet-outbound-tags))

    ;; Advanced options
    (.setProperty props "clientMessageTimeout" (str client-message-timeout))
    (.setProperty props "shouldBundleReplyInfo" (str should-bundle-reply-info))
    (.setProperty props "i2cp.dontPublishLeaseSet" (str i2cp-dont-publish-lease-set))
    (.setProperty props "i2cp.fastReceive" (str i2cp-fast-receive))

    ;; Optional string properties (only set if provided)
    (when inbound-nickname (.setProperty props "inbound.nickname" inbound-nickname))
    (when outbound-nickname (.setProperty props "outbound.nickname" outbound-nickname))
    (when inbound-random-key (.setProperty props "inbound.randomKey" inbound-random-key))
    (when outbound-random-key (.setProperty props "outbound.randomKey" outbound-random-key))
    (when explicit-peers (.setProperty props "explicitPeers" explicit-peers))

    props))

(defn create-client-side-options
  "Create client-side I2CP options Properties object.

  Client-side options control client behavior and session management.
  These are interpreted on the client side via I2PSession.

  Documentation: https://geti2p.net/en/docs/protocol/i2cp#options

  Example usage:
  (create-client-side-options :i2cp-close-on-idle true :i2cp-ssl true)
  (create-client-side-options :i2cp-gzip false :i2cp-tcp-port 7655)"
  [& {:keys [;; Connection and session management
             i2cp-close-on-idle i2cp-close-idle-time
             i2cp-reduce-on-idle i2cp-reduce-idle-time i2cp-reduce-quantity
             i2cp-tcp-host i2cp-tcp-port i2cp-ssl
             i2cp-username i2cp-password

             ;; Data handling
             i2cp-gzip i2cp-fast-receive i2cp-message-reliability

             ;; Lease set configuration
             i2cp-encrypt-lease-set i2cp-lease-set-key
             i2cp-lease-set-type i2cp-lease-set-enc-type
             i2cp-lease-set-private-key i2cp-lease-set-signing-private-key
             i2cp-lease-set-secret i2cp-lease-set-auth-type
             i2cp-lease-set-blinded-type]
      :or {;; Client-side defaults
           i2cp-close-on-idle    false
           i2cp-close-idle-time  1800000
           i2cp-reduce-on-idle   false
           i2cp-reduce-idle-time 1200000
           i2cp-reduce-quantity  1
           i2cp-tcp-host         "127.0.0.1"
           i2cp-tcp-port         7654
           i2cp-ssl              false

           i2cp-gzip                true
           i2cp-fast-receive        true
           i2cp-message-reliability "None"

           i2cp-encrypt-lease-set   false
           i2cp-lease-set-type      1
           i2cp-lease-set-enc-type  0
           i2cp-lease-set-auth-type 0}}]
  (let [props (java.util.Properties.)]
    ;; Connection and session management
    (.setProperty props "i2cp.closeOnIdle" (str i2cp-close-on-idle))
    (.setProperty props "i2cp.closeIdleTime" (str i2cp-close-idle-time))
    (.setProperty props "i2cp.reduceOnIdle" (str i2cp-reduce-on-idle))
    (.setProperty props "i2cp.reduceIdleTime" (str i2cp-reduce-idle-time))
    (.setProperty props "i2cp.reduceQuantity" (str i2cp-reduce-quantity))
    (.setProperty props "i2cp.tcp.host" i2cp-tcp-host)
    (.setProperty props "i2cp.tcp.port" (str i2cp-tcp-port))
    (.setProperty props "i2cp.SSL" (str i2cp-ssl))

    ;; Data handling
    (.setProperty props "i2cp.gzip" (str i2cp-gzip))
    (.setProperty props "i2cp.fastReceive" (str i2cp-fast-receive))
    (.setProperty props "i2cp.messageReliability" i2cp-message-reliability)

    ;; Lease set configuration
    (.setProperty props "i2cp.encryptLeaseSet" (str i2cp-encrypt-lease-set))
    (.setProperty props "i2cp.leaseSetType" (str i2cp-lease-set-type))
    (.setProperty props "i2cp.leaseSetEncType" (str i2cp-lease-set-enc-type))
    (.setProperty props "i2cp.leaseSetAuthType" (str i2cp-lease-set-auth-type))

    ;; Optional string properties (only set if provided)
    (when i2cp-username (.setProperty props "i2cp.username" i2cp-username))
    (when i2cp-password (.setProperty props "i2cp.password" i2cp-password))
    (when i2cp-lease-set-key (.setProperty props "i2cp.leaseSetKey" i2cp-lease-set-key))
    (when i2cp-lease-set-private-key (.setProperty props "i2cp.leaseSetPrivateKey" i2cp-lease-set-private-key))
    (when i2cp-lease-set-signing-private-key (.setProperty props "i2cp.leaseSetSigningPrivateKey" i2cp-lease-set-signing-private-key))
    (when i2cp-lease-set-secret (.setProperty props "i2cp.leaseSetSecret" i2cp-lease-set-secret))
    (when i2cp-lease-set-blinded-type (.setProperty props "i2cp.leaseSetBlindedType" (str i2cp-lease-set-blinded-type)))

    props))
