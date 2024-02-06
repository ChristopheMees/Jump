(ns com.ludicrousowl.jump.mock-servlet
  (:require [com.ludicrousowl.jump.servlet :refer [servlet]])
  (:import [java.io BufferedReader StringReader]
           [jakarta.servlet.http HttpServletRequest HttpServletResponse]))

(defn servlet-request [request]
  (proxy [HttpServletRequest] []
    (getReader [] (BufferedReader. (StringReader. (prn-str (request :body)))))))

(defn servlet-response [response]
  (proxy [HttpServletResponse] []
    (setHeader [name value] (swap! response assoc-in [:headers name] value))
    (setStatus [status] (swap! response assoc :status status))))

(defn mock-servlet [handler]
  (fn [request]
    (let [response (atom {})]
      (.service (servlet handler)
                (servlet-request request)
                (servlet-response response))
      @response)))
