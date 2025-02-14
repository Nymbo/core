(ns components.properties.skill
  (:require [utils.core :refer [readable-number]]
            [core.component :refer [defcomponent] :as component]))

(defcomponent :skill/action-time {:data :pos}
  (component/info-text [[_ v] _ctx]
    (str "[GOLD]Action-Time: " (readable-number v) " seconds[]")))

(defcomponent :skill/cooldown {:data :nat-int}
  (component/info-text [[_ v] _ctx]
    (when-not (zero? v)
      (str "[SKY]Cooldown: " (readable-number v) " seconds[]"))))

(defcomponent :skill/cost {:data :nat-int}
  (component/info-text [[_ v] _ctx]
    (when-not (zero? v)
      (str "[CYAN]Cost: " v " Mana[]"))))

(defcomponent :skill/effects
  {:data [:components-ns :effect]})

(defcomponent :skill/start-action-sound {:data :sound})

(defcomponent :skill/action-time-modifier-key
  {:data [:enum [:stats/cast-speed :stats/attack-speed]]}
  (component/info-text [[_ v] _ctx]
    (str "[VIOLET]" (case v
                      :stats/cast-speed "Spell"
                      :stats/attack-speed "Attack") "[]")))

(defcomponent :properties/skills
  (component/create [_ _ctx]
    {:schema [:entity/image
              :property/pretty-name
              :skill/action-time-modifier-key
              :skill/action-time
              :skill/start-action-sound
              :skill/effects
              [:skill/cooldown {:optional true}]
              [:skill/cost {:optional true}]]
     :overview {:title "Skills"
                :columns 16
                :image/scale 2}}))
