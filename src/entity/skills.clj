(ns entity.skills
  (:require [core.component :as component]
            [data.val-max :refer [apply-val]]
            [api.context :refer [get-property valid-params? ->counter stopped?]]
            [api.entity :as entity]
            [api.tx :refer [transact!]]
            [data.types :as attr]))

; FIXME starting skills do not trigger :tx.context.action-bar/add-skill
; https://trello.com/c/R6GSIDO1/363

; required by npc state, also mana!, also movement (no not needed, doesnt do anything then)
(component/def :entity/skills (attr/one-to-many-ids :property.type/skill)
  skills
  (entity/create-component [_ _components ctx]
    (zipmap skills (map #(get-property ctx %) skills)))

  (entity/tick [[k _] entity* ctx]
    (for [{:keys [property/id skill/cooling-down?]} (vals skills)
          :when (and cooling-down?
                     (stopped? ctx cooling-down?))]
      [:tx/assoc-in (:entity/id entity*) [k id :skill/cooling-down?] false])))

(extend-type api.entity.Entity
  entity/Skills
  (has-skill? [{:keys [entity/skills]} {:keys [property/id]}]
    (contains? skills id)))

(defmethod transact! :tx/add-skill [[_ entity {:keys [property/id] :as skill}]
                                    _ctx]
  (assert (not (entity/has-skill? @entity skill)))
  [[:tx/assoc-in entity [:entity/skills id] skill]
   (when (:entity/player? @entity)
     [:tx.context.action-bar/add-skill skill])])

; unused ?
(defmethod transact! :tx/remove-skill [[_ entity {:keys [property/id] :as skill}]
                                       _ctx]
  (assert (entity/has-skill? @entity skill))
  [[:tx/dissoc-in entity [:entity/skills id]]
   (when (:entity/player? @entity)
     [:tx.context.action-bar/remove-skill skill])])

(extend-type api.context.Context
  api.context/Skills
  (skill-usable-state [effect-context
                       {:keys [entity/mana]}
                       {:keys [skill/cost skill/cooling-down? skill/effect]}]
    (cond
     cooling-down?                               :cooldown
     (and cost (> cost (mana 0)))                :not-enough-mana
     (not (valid-params? effect-context effect)) :invalid-params
     :else                                       :usable)))
