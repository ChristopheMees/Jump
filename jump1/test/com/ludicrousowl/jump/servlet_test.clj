(ns com.ludicrousowl.jump.servlet-test
  (:require [clojure.test :refer :all]

            [com.ludicrousowl.jump.servlet :refer [servlet]]
            [com.ludicrousowl.jump.mock-servlet :refer [servlet-request]])
  (:import [jakarta.servlet.http HttpServletResponse]))

(defn servlet-response []
  (let [response (atom {})]
    (proxy [HttpServletResponse] []
      (getHeader [name] (get-in @response [:headers name]))
      (setHeader [name value] (swap! response assoc-in [:headers name] value))
      (getStatus [] (@response :status))
      (setStatus [status] (swap! response assoc :status status)))))

(deftest post-request
        ;; Handler function that returns a created response using the request body id
  (let [handler (fn [request] {:status 201
                               :headers {"Location"
                                         (str "/api/" (get-in request [:body :id]))}})
        ;; Create the request and response objects
        http-request (servlet-request {:request-method :post :body {:id 1}})
        http-response (servlet-response)]
    ;; Invoke our servlets service method
    (.service (servlet handler)
              http-request
              http-response)
    ;; Assert the response has the 201 status
    (is (= 201 (.getStatus http-response)))
    ;; Assert the response has our headers
    (is (= "/api/1" (.getHeader http-response "Location")))))
