(ns components.screens.map-editor
  (:require [clojure.string :as str]
            [gdx.input :as input]
            [gdx.input.keys :as input.keys]
            [gdx.graphics.color :as color]
            [gdx.graphics.camera :as camera]
            [gdx.graphics.orthographic-camera :as orthographic-camera]
            [gdx.utils.disposable :refer [dispose]]
            [core.component :refer [defcomponent] :as component]
            [utils.core :refer [->tile]]
            utils.camera
            [core.context :as ctx :refer [->label ->window ->actor ->tiled-map ->text-button current-screen]]
            [core.graphics :as g]
            [core.maps.tiled :as tiled]
            [core.scene2d.actor :refer [set-position!]]
            [core.scene2d.group :refer [add-actor!]]
            [core.scene2d.ui.widget-group :refer [pack!]]
            [core.scene2d.ui.label :refer [set-text!]]
            mapgen.gen
            mapgen.modules))

; TODO map-coords are clamped ? thats why showing 0 under and left of the map?
; make more explicit clamped-map-coords ?

; TODO
; leftest two tiles are 0 coordinate x
; and rightest is 16, not possible -> check clamping
; depends on screen resize or something, changes,
; maybe update viewport not called on resize sometimes

(defn- show-whole-map! [camera tiled-map]
  (camera/set-position! camera
                        [(/ (tiled/width  tiled-map) 2)
                         (/ (tiled/height tiled-map) 2)])
  (orthographic-camera/set-zoom! camera
                                 (utils.camera/calculate-zoom camera
                                                              :left [0 0]
                                                              :top [0 (tiled/height tiled-map)]
                                                              :right [(tiled/width tiled-map) 0]
                                                              :bottom [0 0])))

(defn- current-data [ctx]
  (-> ctx
    current-screen
    (get 1)
    :sub-screen
    (get 1)))

(def ^:private infotext
  "L: grid lines
M: movement properties
zoom: shift-left,minus
ESCAPE: leave
direction keys: move")

(defn- debug-infos [ctx]
  (let [tile (->tile (ctx/world-mouse-position ctx))
        {:keys [tiled-map
                area-level-grid]} @(current-data ctx)]
    (->> [infotext
          (str "Tile " tile)
          (when-not area-level-grid
            (str "Module " (mapv (comp int /)
                                 (ctx/world-mouse-position ctx)
                                 [mapgen.modules/module-width
                                  mapgen.modules/module-height])))
          (when area-level-grid
            (str "Creature id: " (tiled/property-value tiled-map :creatures tile :id)))
          (when area-level-grid
            (let [level (get area-level-grid tile)]
              (when (number? level)
                (str "Area level:" level))))
          (str "Movement properties " (tiled/movement-property tiled-map tile) "\n"
               (apply vector (tiled/movement-properties tiled-map tile)))]
         (remove nil?)
         (str/join "\n"))))

; same as debug-window
(defn- ->info-window [ctx]
  (let [label (->label ctx "")
        window (->window ctx {:title "Info" :rows [[label]]})]
    (add-actor! window (->actor ctx {:act #(do
                                            (set-text! label (debug-infos %))
                                            (pack! window))}))
    (set-position! window 0 (ctx/gui-viewport-height ctx))
    window))

(defn- adjust-zoom [camera by] ; DRY context.game
  (orthographic-camera/set-zoom! camera (max 0.1 (+ (orthographic-camera/zoom camera) by))))

; TODO movement-speed scales with zoom value for big maps useful
(def ^:private camera-movement-speed 1)
(def ^:private zoom-speed 0.1)

; TODO textfield takes control !
; TODO PLUS symbol shift & = symbol on keyboard not registered
(defn- camera-controls [context camera]
  (when (input/key-pressed? input.keys/shift-left)
    (adjust-zoom camera    zoom-speed))
  (when (input/key-pressed? input.keys/minus)
    (adjust-zoom camera (- zoom-speed)))
  (let [apply-position (fn [idx f]
                         (camera/set-position! camera
                                               (update (camera/position camera)
                                                       idx
                                                       #(f % camera-movement-speed))))]
    (if (input/key-pressed? input.keys/left)  (apply-position 0 -))
    (if (input/key-pressed? input.keys/right) (apply-position 0 +))
    (if (input/key-pressed? input.keys/up)    (apply-position 1 +))
    (if (input/key-pressed? input.keys/down)  (apply-position 1 -))))

#_(def ^:private show-area-level-colors true)
; TODO unused
; TODO also draw numbers of area levels big as module size...

(defn- render-on-map [g ctx]
  (let [{:keys [tiled-map
                area-level-grid
                start-position
                show-movement-properties
                show-grid-lines]} @(current-data ctx)
        visible-tiles (utils.camera/visible-tiles (ctx/world-camera ctx))
        [x y] (->tile (ctx/world-mouse-position ctx))]
    (g/draw-rectangle g x y 1 1 color/white)
    (when start-position
      (g/draw-filled-rectangle g (start-position 0) (start-position 1) 1 1 [1 0 1 0.9]))
    ; TODO move down to other doseq and make button
    (when show-movement-properties
      (doseq [[x y] visible-tiles
              :let [movement-property (tiled/movement-property tiled-map [x y])]]
        (g/draw-filled-circle g [(+ x 0.5) (+ y 0.5)]
                              0.08
                              color/black)
        (g/draw-filled-circle g [(+ x 0.5) (+ y 0.5)]
                              0.05
                              (case movement-property
                                "all"   color/green
                                "air"   color/orange
                                "none"  color/red))))
    (when show-grid-lines
      (g/draw-grid g 0 0 (tiled/width  tiled-map) (tiled/height tiled-map) 1 1 [1 1 1 0.5]))))

(def ^:private world-id :worlds/modules)

(defn- generate [context properties]
  (let [;{:keys [tiled-map area-level-grid start-position]} (mapgen.gen/generate context properties)
        {:keys [tiled-map start-position]} (ctx/->world context world-id)
        atom-data (current-data context)]
    (dispose (:tiled-map @atom-data))
    (swap! atom-data assoc
           :tiled-map tiled-map
           ;:area-level-grid area-level-grid
           :start-position start-position)
    (show-whole-map! (ctx/world-camera context) tiled-map)
    (.setVisible (tiled/get-layer tiled-map "creatures") true)
    context))

(defn ->generate-map-window [ctx level-id]
  (->window ctx {:title "Properties"
                 :cell-defaults {:pad 10}
                 :rows [[(->label ctx (with-out-str
                                       (clojure.pprint/pprint
                                        (ctx/property ctx level-id))))]
                        [(->text-button ctx "Generate" #(try (generate % (ctx/property % level-id))
                                                             (catch Throwable t
                                                               (ctx/error-window! % t)
                                                               (println t)
                                                               %)))]]
                 :pack? true}))

(defcomponent ::sub-screen
  {:let current-data}
  ; TODO ?
  ;com.badlogic.gdx.utils.Disposable
  #_(dispose [_]
    (dispose (:tiled-map @current-data)))

  (component/enter [_ ctx]
    (show-whole-map! (ctx/world-camera ctx) (:tiled-map @current-data)))

  (component/exit [_ ctx]
    (orthographic-camera/reset-zoom! (ctx/world-camera ctx)))

  (component/render-ctx [_ context]
    (ctx/render-tiled-map context
                          (:tiled-map @current-data)
                          (constantly color/white))
    (ctx/render-world-view context #(render-on-map % context))
    (if (input/key-just-pressed? input.keys/l)
      (swap! current-data update :show-grid-lines not))
    (if (input/key-just-pressed? input.keys/m)
      (swap! current-data update :show-movement-properties not))
    (camera-controls context (ctx/world-camera context))
    (if (input/key-just-pressed? input.keys/escape)
      (ctx/change-screen context :screens/main-menu)
      context)))

(derive :screens/map-editor :screens/stage-screen)
(defcomponent :screens/map-editor
  (component/create [_ ctx]
    {:sub-screen [::sub-screen
                  (atom {:tiled-map (->tiled-map ctx mapgen.modules/modules-file)
                         :show-movement-properties false
                         :show-grid-lines false})]
     :stage (ctx/->stage ctx [(->generate-map-window ctx world-id)
                              (->info-window ctx)])}))
