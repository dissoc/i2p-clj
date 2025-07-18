# Project Summary: i2p-clj

## Overview
`i2p-clj` is a comprehensive Clojure library for I2P (Invisible Internet Project) anonymous network integration. It provides high-level abstractions for I2P socket management, router configuration, and client-server communication, enabling Clojure applications to communicate securely over the I2P anonymity network.

## Current Status
- **Version**: 0.1.0 (stable release)
- **Language**: Clojure 1.11.1
- **Build Tool**: Leiningen
- **License**: EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0
- **Author**: Justin Bishop (dissoc)
- **Repository**: https://github.com/dissoc/i2p-clj

## Project Structure
```
i2p-clj/
├── src/
│   └── i2p_clj/
│       ├── artemis.clj         # Apache ActiveMQ Artemis integration (work in progress)
│       ├── client.clj          # I2P client connections with retry logic and async handling
│       ├── config.clj          # Configuration management using cprop
│       ├── core.clj            # Core transit messaging utilities for data serialization
│       ├── manager.clj         # I2P socket manager creation and comprehensive configuration
│       ├── router.clj          # Complete I2P router creation and configuration
│       ├── server.clj          # I2P server with concurrent connection handling
│       └── util.clj            # Utility functions for I2P destinations and key generation
├── test/
│   └── i2p_clj/
│       ├── core_test.clj       # Test suite for core messaging functionality
│       ├── echo_test.clj       # Integration tests for server-client communication
│       ├── manager_test.clj    # Tests for socket manager and configuration options
│       └── router_test.clj     # Tests for router creation and configuration
├── env/
│   ├── dev/resources/          # Development environment configuration
│   │   └── config.edn          # Development config file
│   └── test/resources/         # Test environment configuration
│       └── config.edn          # Test config file
├── doc/
│   └── intro.md                # Project documentation (placeholder)
├── resources/                  # Runtime resources
└── project.clj                 # Leiningen project configuration
```

## Core Features

### I2P Socket Management (`i2p-clj.manager`)
- **Flexible Socket Manager Creation**: Comprehensive I2P socket manager with support for all I2CP configuration options
- **Connection Filtering**: Configurable connection acceptance policies using custom filter functions
- **Router-side Configuration**: Complete tunnel configuration including length, quantity, backup, variance, and cryptographic settings
- **Client-side Configuration**: Session management, SSL, gzip, lease set configuration, and connection pooling options
- **I2CP Protocol Support**: Full integration with I2P Client Protocol for advanced tunnel and session management

### Client Connections (`i2p-clj.client`)
- **Automatic Retry Logic**: Configurable connection retry with exponential backoff for resilient connections
- **Asynchronous Message Handling**: Non-blocking message processing with configurable callback functions
- **Connection Lifecycle Management**: Automatic connection establishment to remote I2P destinations with proper resource cleanup
- **Error Handling**: Comprehensive error handling and connection recovery mechanisms
- **Socket Reader Loop**: Continuous message reading with proper stream management

### Server Implementation (`i2p-clj.server`)
- **Concurrent Connection Handling**: Multi-client server support with proper thread management and resource isolation
- **Connection Filtering**: Server-side connection acceptance policies for security and access control
- **Graceful Shutdown**: Proper resource cleanup and connection termination handling
- **Real-time Message Processing**: Live message processing with configurable handlers for each client connection
- **Server Socket Management**: Complete server lifecycle management with proper error handling

### Router Management (`i2p-clj.router`)
- **Complete Router Creation**: Full I2P router instantiation with comprehensive configuration options
- **Bandwidth Configuration**: Inbound/outbound bandwidth limits, burst settings, and traffic shaping
- **Transport Settings**: NTCP and UDP transport configuration with UPnP and firewall support
- **Security Configuration**: Dynamic keys, participating tunnels, sharing percentage, and floodfill settings
- **Directory Management**: Flexible I2P directory structure configuration for data persistence
- **Console Integration**: Router console configuration and administrative interface settings

### Core Messaging (`i2p-clj.core`)
- **Transit Serialization**: Efficient Clojure data structure serialization over I2P connections
- **Binary Message Protocol**: High-performance binary message format with length prefixing
- **Stream-based Communication**: Reliable message streaming utilities for I2P socket connections

### Configuration Management (`i2p-clj.config`)
- **Environment-based Configuration**: Support for development, test, and production configurations
- **Property Loading**: Integration with system properties and environment variables
- **Flexible Configuration Sources**: Support for multiple configuration sources and merging strategies

### Utility Functions (`i2p-clj.util`)
- **Destination Management**: Base32/Base64 address to I2P destination conversion and validation
- **Key Generation**: I2P signing key pair generation with EdDSA-SHA512-Ed25519 signature type
- **Transit Integration**: High-performance Clojure data structure serialization/deserialization
- **Address Resolution**: I2P naming service integration for destination lookups

### Messaging Integration (`i2p-clj.artemis`)
- **ActiveMQ Artemis Integration**: Work-in-progress integration with Apache ActiveMQ Artemis message broker
- **Protocol Factories**: I2P-specific protocol manager factories for message broker integration
- **Custom Acceptors**: Development of custom I2P acceptor implementations for messaging systems

## Key Dependencies
- **I2P Core Libraries**: 
  - `net.i2p/i2p` 2.7.0 - Core I2P functionality and routing
  - `net.i2p/router` 2.7.0 - I2P router implementation
  - `net.i2p.client/streaming` 2.7.0 - I2P streaming API
  - `net.i2p.client/mstreaming` 2.7.0 - Multi-streaming support
- **Transaction Management**: 
  - `org.jboss.narayana.jta/narayana-jta` 7.1.0.Final - JTA transaction manager (for future use)
  - `org.jboss.logging/jboss-logging` 3.6.1.Final - Logging framework
- **Configuration & Lifecycle**: 
  - `cprop` 0.1.20 - Configuration management with environment support
  - `mount` 0.1.20 - State management and component lifecycle
- **Logging**: 
  - `com.taoensso/timbre` 6.7.0 - Clojure logging library
  - `ch.qos.logback/logback-classic` 1.5.18 - SLF4J implementation
- **Serialization**: 
  - `com.cognitect/transit-clj` 1.0.333 - High-performance data serialization
- **Utilities**: 
  - `clj-commons/fs` 1.6.307 - File system utilities
  - `clojure.java-time` 1.4.3 - Date/time handling

## Development Environment
- **REPL Port**: 7888 (development profile)
- **CIDER Integration**: cider-nrepl 0.57.0 for Emacs/CIDER development
- **AOT Compilation**: Pre-compiled classes for `i2p-clj.i2p-xaresource` and `i2p-clj.core`
- **Main Namespace**: `i2p-clj.core` for application entry point
- **GitHub Packages**: Configured for deployment to GitHub Maven registry with GPG signing
- **JVM Options**: Configured for Narayana transaction manager debugging and JDK attach capabilities

## Configuration Profiles
- **Development**: Uses `dev-config.edn` with REPL port 7888 and development-specific resources
- **Test**: Uses `test-config.edn` with test-specific configuration and resources
- **Production**: Runtime configuration via cprop with environment variable and system property support

## Testing Suite
- **Core Tests**: Unit tests for transit messaging and data serialization (`core_test.clj`)
- **Integration Tests**: Full server-client communication tests with echo functionality (`echo_test.clj`)
- **Manager Tests**: Comprehensive tests for socket manager creation and configuration options (`manager_test.clj`)
- **Router Tests**: Tests for router creation, configuration, and lifecycle management (`router_test.clj`)

## Architecture Highlights

### Layered Architecture
The library follows a clean layered architecture:
1. **Utility Layer**: Core I2P destination handling, key management, and serialization
2. **Socket Layer**: I2P socket creation and management with comprehensive configuration
3. **Connection Layer**: Client/server connection abstractions with retry logic and error handling
4. **Application Layer**: High-level APIs for building I2P-enabled applications

### Separation of Concerns
- **Router-side vs Client-side**: Clear separation between tunnel behavior and session management configuration
- **Configuration Management**: Environment-specific configuration with sensible defaults and validation
- **Resource Management**: Proper lifecycle management with automatic cleanup and error recovery
- **Protocol Abstraction**: High-level APIs that hide I2P protocol complexity from application developers

### Production-Ready Features
- **Concurrent Processing**: Multi-threaded server and client implementations with proper synchronization
- **Resource Cleanup**: Automatic connection and resource management with graceful shutdown
- **Retry Logic**: Configurable retry mechanisms for network resilience and fault tolerance
- **Comprehensive Testing**: Full test suite covering unit, integration, and end-to-end scenarios
- **Error Handling**: Robust error handling with detailed logging and recovery mechanisms

## Example Usage

### Basic Client-Server Communication
```clojure
(require '[i2p-clj.client :as client]
         '[i2p-clj.server :as server]
         '[i2p-clj.manager :as manager]
         '[i2p-clj.util :as util])

;; Create destinations
(def server-dest (util/create-destination))
(def client-dest (util/create-destination))

;; Create server
(def my-server 
  (server/create-i2p-socket-server 
    {:destination-key (:key-stream-base-32 server-dest)
     :connection-filter #(println "Connection from:" %)
     :on-receive (fn [{:keys [data-input-stream data-output-stream]}]
                   (let [msg (i2p-clj.core/reader data-input-stream)]
                     (println "Server received:" msg)
                     (i2p-clj.core/sender data-output-stream msg)))}))

;; Create client
(def my-client 
  (client/create-i2p-socket-client 
    {:destination-key (:key-stream-base-32 client-dest)
     :remote-address (:address-base-64 server-dest)
     :on-receive (fn [{:keys [data-input-stream]}]
                   (println "Client received:" 
                           (i2p-clj.core/reader data-input-stream)))
     :retry-attempts 5}))
```

### Advanced Router Configuration
```clojure
(require '[i2p-clj.router :as router])

(def high-performance-router
  (router/create-router 
    :config (router/create-router-config 
              :router-dir "/opt/i2p-data"
              :i2np-bandwidth-inbound-kb-per-sec 2048
              :i2np-bandwidth-outbound-kb-per-sec 2048
              :router-max-participating-tunnels 1000
              :router-share-percentage 95
              :i2np-ntcp-enable true
              :i2np-udp-enable true
              :i2np-upnp-enable true)))
```

### Custom Socket Manager Configuration
```clojure
(require '[i2p-clj.manager :as manager])

(def custom-manager 
  (manager/create-socket-manager
    :destination-key "my-destination.b32"
    :connection-filter #(allowed-destination? %)
    :options (manager/create-router-side-options
               :inbound-length 4
               :outbound-length 4
               :inbound-quantity 3
               :outbound-quantity 3
               :crypto-tags-to-send 80
               :crypto-ratchet-inbound-tags 200)))
```

## Build and Deployment

### Development Commands
```bash
# Start development REPL with CIDER support
lein repl

# Run complete test suite
lein test

# Run specific test namespace
lein test i2p-clj.echo-test

# Build with AOT compilation
lein compile

# Deploy to GitHub packages (requires credentials)
lein deploy github
```

### Configuration
Environment-specific configuration files:
- `env/dev/resources/config.edn` - Development settings and debugging options
- `env/test/resources/config.edn` - Test environment settings with test-specific parameters
- Runtime configuration via environment variables and system properties using cprop

## Use Cases

### Anonymous Communication Applications
- Secure messaging systems requiring network-level anonymity and privacy
- Anonymous file sharing and distributed storage systems
- Privacy-focused chat applications, forums, and social networks
- Whistleblowing and journalism platforms requiring source protection

### Distributed Systems
- Anonymous distributed computing networks and grid computing
- Decentralized applications requiring privacy and censorship resistance
- Anonymous blockchain and cryptocurrency services
- Peer-to-peer applications with strong privacy guarantees

### Enterprise Integration
- Anonymous B2B communication channels for sensitive business data
- Secure inter-organization data exchange in regulated industries
- Privacy-compliant messaging systems for healthcare, finance, and legal sectors
- Corporate communication systems requiring plausible deniability

### High-Performance I2P Services
- Production servers with custom bandwidth optimization and tunnel configuration
- Large-scale anonymous communication platforms and services
- Performance-critical anonymous network services and applications
- I2P-based CDN and content distribution networks

## Technical Considerations

### Performance Optimizations
- **Concurrent Processing**: Multi-threaded connection handling with proper thread pool management
- **Efficient Serialization**: High-performance transit-based data serialization for minimal overhead
- **Connection Pooling**: Efficient I2P connection resource management and reuse
- **Bandwidth Management**: Configurable bandwidth limits, burst settings, and traffic shaping
- **Memory Management**: Careful resource management to prevent memory leaks in long-running applications

### Security Features
- **Anonymous Networking**: Full I2P anonymity network integration with proper operational security
- **Encrypted Tunnels**: Configurable tunnel lengths, backup quantities, and encryption parameters
- **Destination Security**: I2P destination key generation and management with secure defaults
- **Connection Filtering**: Flexible connection filtering for access control and security policies
- **Lease Set Security**: Support for encrypted and private lease sets for enhanced privacy

### Reliability Features
- **Automatic Retry Logic**: Configurable connection retry with exponential backoff and jitter
- **Comprehensive Error Handling**: Robust error handling and recovery mechanisms at all layers
- **Resource Management**: Automatic cleanup and lifecycle management for all I2P resources
- **Graceful Degradation**: Fallback mechanisms for network issues and router connectivity problems
- **Monitoring and Health Checks**: Built-in monitoring capabilities for connection and router health

### Monitoring and Debugging
- **Comprehensive Logging**: Structured logging with Timbre and Logback for production monitoring
- **Transaction Debugging**: Narayana transaction manager debug options for distributed systems
- **JVM Monitoring**: JDK attach self capability for runtime monitoring and profiling
- **Router Console**: Built-in I2P router console access for network monitoring and administration
- **Test Coverage**: Extensive test suite with integration tests for real-world scenario validation

## Development Roadmap

### Completed Features
- Core I2P socket management and configuration
- Client-server communication with retry logic
- Router creation and comprehensive configuration
- Transit-based message serialization
- Comprehensive test suite with integration tests

### In Progress
- ActiveMQ Artemis integration for enterprise messaging
- Enhanced documentation and usage examples
- Performance optimization and benchmarking

### Future Plans
- SAM (Simple Anonymous Messaging) protocol support
- Distributed transaction support with XA resources
- WebSocket proxy for browser-based I2P applications
- Performance monitoring and metrics collection
- Plugin system for extensible functionality

---
*Last updated: July 18, 2025*
