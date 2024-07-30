(ns effect.melee-damage
  (:require [core.component :as component]
            [api.effect :as effect]
            [api.tx :refer [transact!]]))

(defn- entity*->melee-damage [entity*]
  (let [strength (or (:stats/strength (:entity/stats entity*))
                     0)]
    {:damage/min-max [strength strength]}))

(component/def :effect/melee-damage {}
  _
  (effect/text [_ {:keys [effect/source] :as ctx}]
    (if source
      (effect/text [:effect/damage (entity*->melee-damage @source)] ctx)
      "Damage based on entity stats."))

  (effect/valid-params? [_ {:keys [effect/source effect/target]}]
    (and source target))

  (transact! [_ {:keys [effect/source]}]
    [[:effect/damage (entity*->melee-damage @source)]]))
