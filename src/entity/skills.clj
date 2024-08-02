(ns entity.skills
  (:require [core.component :refer [defcomponent]]
            [data.val-max :refer [apply-val]]
            [api.context :refer [get-property valid-params? ->counter stopped?]]
            [api.entity :as entity]
            [api.tx :refer [transact!]]
            [core.data :as attr]))

; FIXME starting skills do not trigger :tx.context.action-bar/add-skill
; https://trello.com/c/R6GSIDO1/363

; required by npc state, also mana!, also movement (no not needed, doesnt do anything then)
(defcomponent :entity/skills (attr/one-to-many-ids :properties/skill)
  (entity/create-component [[_ skill-ids] _components ctx]
    (zipmap skill-ids (map #(get-property ctx %) skill-ids)))

  (entity/tick [[k skills] entity* ctx]
    (for [{:keys [property/id skill/cooling-down?]} (vals skills)
          :when (and cooling-down?
                     (stopped? ctx cooling-down?))]
      [:tx.entity/assoc-in (:entity/id entity*) [k id :skill/cooling-down?] false])))

(extend-type api.entity.Entity
  entity/Skills
  (has-skill? [{:keys [entity/skills]} {:keys [property/id]}]
    (contains? skills id)))

(defmethod transact! :tx/add-skill [[_ entity {:keys [property/id] :as skill}]
                                    _ctx]
  (assert (not (entity/has-skill? @entity skill)))
  [[:tx.entity/assoc-in entity [:entity/skills id] skill]
   (when (:entity/player? @entity)
     [:tx.context.action-bar/add-skill skill])])

; unused ?
(defmethod transact! :tx/remove-skill [[_ entity {:keys [property/id] :as skill}]
                                       _ctx]
  (assert (entity/has-skill? @entity skill))
  [[:tx.entity/dissoc-in entity [:entity/skills id]]
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
