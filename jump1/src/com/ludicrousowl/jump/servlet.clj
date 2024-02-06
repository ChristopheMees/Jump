(ns com.ludicrousowl.jump.servlet
  (:require [clojure.edn :as edn])
  (:import [java.io PushbackReader]
           [jakarta.servlet.http HttpServlet]))

(defn- set-response-values [http-response response]
  (doseq [[header value] (response :headers)]
    (.setHeader http-response header value))
  (.setStatus http-response (response :status)))

(defn- request-map [http-request]
  {:body (edn/read (PushbackReader. (.getReader http-request)))})

(defn servlet [handler]
  (proxy [HttpServlet] []
    (service [http-request http-response]
      (->> (request-map http-request)
           handler
           (set-response-values http-response)))))

