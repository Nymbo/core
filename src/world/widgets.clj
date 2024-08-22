(ns world.widgets
  (:require [gdx.input :as input]
            [gdx.input.keys :as input.keys]
            [gdx.scene2d.stage :as stage]
            [utils.core :as utils]
            [core.component :refer [defcomponent] :as component]
            [api.context :as ctx]
            [api.scene2d.actor :as actor]
            [api.scene2d.group :as group]
            api.scene2d.ui.button-group
            [entity-state.player-item-on-cursor :refer [draw-item-on-cursor]]
            [widgets.player-message :as player-message]
            [widgets.action-bar :as action-bar]
            [widgets.debug-window :as debug-window]
            [widgets.entity-info-window :as entity-info-window]
            [widgets.inventory :as inventory]
            [widgets.hp-mana-bars :refer [->hp-mana-bars]]
            widgets.player-modal))

(defn- ->action-bar-table [ctx]
  (ctx/->table ctx {:rows [[{:actor (action-bar/->build ctx)
                             :expand? true
                             :bottom? true}]]
                    :id :action-bar-table
                    :cell-defaults {:pad 2}
                    :fill-parent? true}))

(extend-type api.context.Context
  api.context/Actionbar
  (selected-skill [ctx]
    (let [button-group (:action-bar (:world/widgets ctx))]
      (when-let [skill-button (api.scene2d.ui.button-group/checked button-group)]
        (actor/id skill-button))))

  api.context/InventoryWindow
  (inventory-window [ctx]
    (get (:windows (ctx/get-stage ctx)) :inventory-window)))

(defn- ->windows [context widget-data]
  (ctx/->group context {:id :windows
                        :actors [(debug-window/create context)
                                 (entity-info-window/create context)
                                 (inventory/->build context widget-data)]}))

(defn- ->item-on-cursor-actor [context]
  (ctx/->actor context {:draw draw-item-on-cursor}))

(defn- ->ui-actors [ctx widget-data]
  [(->action-bar-table     ctx)
   (->hp-mana-bars         ctx)
   (->windows              ctx widget-data)
   (->item-on-cursor-actor ctx)
   (player-message/->build ctx)])

(defn- reset-stage-actors! [ctx widget-data]
  (assert (= :screens/world (ctx/current-screen-key ctx)))
  (doto (ctx/get-stage ctx)
    stage/clear!
    (stage/add-actors! (->ui-actors ctx widget-data))))

(defcomponent :world/widgets {}
  (component/create [_ ctx]
    (let [widget-data {:action-bar (action-bar/->button-group ctx)
                       :slot->background (inventory/->data ctx)}]
      (reset-stage-actors! ctx widget-data)
      widget-data)))

(defn- hotkey->window-id [{:keys [context/config] :as ctx}]
  (merge
   {input.keys/i :inventory-window
    input.keys/e :entity-info-window}
   (when (utils/safe-get config :debug-window?)
     {input.keys/z :debug-window})))

(defn check-window-hotkeys [ctx]
  (doseq [[hotkey window-id] (hotkey->window-id ctx)
          :when (input/key-just-pressed? hotkey)]
    (actor/toggle-visible! (get (:windows (ctx/get-stage ctx)) window-id))))

(defn close-windows? [context]
  (let [windows (group/children (:windows (ctx/get-stage context)))]
    (if (some actor/visible? windows)
      (do
       (run! #(actor/set-visible! % false) windows)
       true))))
