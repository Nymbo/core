(ns screens.game
  (:require [core.component :refer [defcomponent]]
            [api.context :as ctx]
            [api.screen :as screen :refer [Screen]]
            [context.game :as game]))

(defrecord SubScreen []
  Screen
  (show [_ _ctx])
  (hide [_ ctx]
    (ctx/set-cursor! ctx :cursors/default))
  (render [_ ctx]
    (game/render ctx)))

(defcomponent :screens/game {}
  (screen/create [_ ctx]
    (ctx/->stage-screen ctx
                        {:actors []
                         :sub-screen (->SubScreen)})))
