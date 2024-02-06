(ns com.ludicrousowl.jump.mock-servlet-test
  (:require [clojure.test :refer :all]

            [com.ludicrousowl.jump.mock-servlet :refer [mock-servlet]]))

(deftest invoke-mock-with-request-map
  (is (= {:status 200} ((mock-servlet (constantly {:status 200})) {}))))
