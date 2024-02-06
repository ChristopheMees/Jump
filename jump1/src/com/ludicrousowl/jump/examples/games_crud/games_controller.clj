(ns com.ludicrousowl.jump.examples.games-crud.games-controller)

(defn create [game]
  {:status 201
   :headers {"Location" (str "/games/" (game :id))}})
