(ns com.ludicrousowl.jump.examples.games-crud.games-controller-test
  (:require [clojure.test :refer :all]

            [com.ludicrousowl.jump.mock-servlet :refer [mock-servlet]]
            [com.ludicrousowl.jump.examples.games-crud.games-controller :refer [create]]))

(deftest create-a-game
  (let [request {:request-method :post
                 :uri "/games"
                 :body {:id 5
                        :title "Dark Souls"
                        :releaseDate "2011-09-22"
                        :develop "FromSoftware"}}
        handler (fn [request] (create (request :body)))
        response ((mock-servlet handler) request)]
    (is (= 201 (response :status)))
    (is (= "/games/5" (get-in response [:headers "Location"])))))
