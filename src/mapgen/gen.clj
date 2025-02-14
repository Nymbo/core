(ns mapgen.gen
  (:require [utils.core :refer [assoc-ks]]
            [data.grid2d :as grid]
            [core.maps.tiled :as tiled]
            [core.context :as ctx :refer [->tiled-map all-properties]]
            [core.property :as property]
            [mapgen.utils :refer [printgrid scale-grid]]
            [mapgen.tiled-utils :refer [->static-tiled-map-tile set-tile! put! add-layer!]]
            [mapgen.transitions :as transitions]
            [mapgen.cave-gen :as cave-gen]
            [mapgen.nad :as nad]
            mapgen.modules
            [mapgen.area-level-grid :refer [->area-level-grid]])
  (:import java.util.Random))

; TODO generates 51,52. not max 50
; TODO can use different turn-ratio/depth/etc. params
; (printgrid (:grid (->cave-grid :size 800)))
(defn- ->cave-grid [& {:keys [size]}]
  (let [{:keys [start grid]} (cave-gen/cave-gridgen (Random.) size size :wide)
        grid (nad/fix-not-allowed-diagonals grid)]
    (assert (= #{:wall :ground} (set (grid/cells grid))))
    {:start start
     :grid grid}))


; TODO HERE
; * unique max 16 modules, not random take @ #'floor->module-index, also special start, end modules, rare modules...

; * at the beginning enemies very close, different area different spawn-rate !

; beginning slow enemies low hp low dmg etc.

; * flood-fill gets 8 neighbour posis -> no NADs on modules ! assert !

; * assuming bottom left in floor module is walkable

; whats the assumption here? => or put extra borders around? / assert!

(defn- adjacent-wall-positions [grid]
  (filter (fn [p] (and (= :wall (get grid p))
                       (some #(= :ground (get grid %))
                             (grid/get-8-neighbour-positions p))))
          (grid/posis grid)))

(defn- flood-fill [grid start walk-on-position?]
  (loop [next-positions [start]
         filled []
         grid grid]
    (if (seq next-positions)
      (recur (filter #(and (get grid %)
                           (walk-on-position? %))
                     (distinct
                      (mapcat grid/get-8-neighbour-positions
                              next-positions)))
             (concat filled next-positions)
             (assoc-ks grid next-positions nil))
      filled)))

(comment
 (let [{:keys [start grid]} (->cave-grid :size 15)
       _ (println "BASE GRID:\n")
       _ (printgrid grid)
       ;_ (println)
       ;_ (println "WITH START POSITION (0) :\n")
       ;_ (printgrid (assoc grid start 0))
       ;_ (println "\nwidth:  " (grid/width  grid)
       ;           "height: " (grid/height grid)
       ;           "start " start "\n")
       ;_ (println (grid/posis grid))
       _ (println "\n\n")
       filled (flood-fill grid start (fn [p] (= :ground (get grid p))))
       _ (printgrid (reduce #(assoc %1 %2 nil) grid filled))])
 )


(defn- creatures-with-level [creature-properties level]
  (filter #(= level (:creature/level %)) creature-properties))

(def ^:private creature->tile
  (memoize
   (fn [{:keys [property/id] :as prop}]
     (assert id)
     (let [image (property/->image prop)
           tile (->static-tiled-map-tile (:texture-region image))]
       (put! (tiled/properties tile) "id" id)
       tile))))

(def ^:private spawn-creatures? true)

(defn- place-creatures! [context spawn-rate tiled-map spawn-positions area-level-grid]
  (let [layer (add-layer! tiled-map :name "creatures" :visible false)
        creature-properties (all-properties context :properties/creatures)]
    (when spawn-creatures?
      (doseq [position spawn-positions
              :let [area-level (get area-level-grid position)]
              :when (and (number? area-level)
                         (<= (rand) spawn-rate))]
        (let [creatures (creatures-with-level creature-properties area-level)]
          (when (seq creatures)
            (set-tile! layer position (creature->tile (rand-nth creatures)))))))))

(defn generate
  "The generated tiled-map needs to be disposed."
  [context {:keys [world/map-size
                   world/max-area-level
                   world/spawn-rate]}]
  (assert (<= max-area-level map-size))
  (let [{:keys [start grid]} (->cave-grid :size map-size)
        ;_ (printgrid grid)
        ;_ (println " - ")
        grid (reduce #(assoc %1 %2 :transition) grid (adjacent-wall-positions grid))
        ;_ (printgrid grid)
        ;_ (println " - ")
        _ (assert (or
                   (= #{:wall :ground :transition} (set (grid/cells grid)))
                   (= #{:ground :transition} (set (grid/cells grid))))
                  (str "(set (grid/cells grid)): " (set (grid/cells grid))))
        scale mapgen.modules/scale
        scaled-grid (scale-grid grid scale)
        tiled-map (mapgen.modules/place-modules
                   (->tiled-map context mapgen.modules/modules-file)
                   scaled-grid
                   grid
                   (filter #(= :ground     (get grid %)) (grid/posis grid))
                   (filter #(= :transition (get grid %)) (grid/posis grid)))
        start-position (mapv * start scale)
        can-spawn? #(= "all" (tiled/movement-property tiled-map %))
        _ (assert (can-spawn? start-position)) ; assuming hoping bottom left is movable
        spawn-positions (flood-fill scaled-grid start-position can-spawn?)
        ;_ (println "scaled grid with filled nil: '?' \n")
        ;_ (printgrid (reduce #(assoc %1 %2 nil) scaled-grid spawn-positions))
        ;_ (println "\n")
        {:keys [steps area-level-grid]} (->area-level-grid :grid grid
                                                           :start start
                                                           :max-level max-area-level
                                                           :walk-on #{:ground :transition})
        ;_ (printgrid area-level-grid)
        _ (assert (or
                   (= (set (concat [max-area-level] (range max-area-level)))
                      (set (grid/cells area-level-grid)))
                   (= (set (concat [:wall max-area-level] (range max-area-level)))
                      (set (grid/cells area-level-grid)))))
        scaled-area-level-grid (scale-grid area-level-grid scale)
        get-free-position-in-area-level (fn [area-level]
                                          (rand-nth
                                           (filter
                                            (fn [p]
                                              (and (= area-level (get scaled-area-level-grid p))
                                                   (#{:no-cell :undefined}
                                                    (tiled/property-value tiled-map :creatures p :id))))
                                            spawn-positions)))]
    (place-creatures! context spawn-rate tiled-map spawn-positions scaled-area-level-grid)
    {:tiled-map tiled-map
     :start-position (get-free-position-in-area-level 0)
     :area-level-grid scaled-area-level-grid}))

(require '[gdx.graphics.g2d :refer [->texture-region]])

(defn uf-transition [position grid]
  (transitions/index-value position (= :transition (get grid position))))

(defn rand-0-3 []
  (utils.random/get-rand-weighted-item
   {0 60 1 1 2 1 3 1}))

(defn rand-0-5 []
  (utils.random/get-rand-weighted-item
   {0 30 1 1 2 1 3 1 4 1 5 1}))

; TODO zoomed out see that line of sight raycast goes x screens away
; only becuz of zoom?

; Level:
; * ground textures
; * wall textures
; * doodads ?
; * creatures w. that lvl
; (skills/items)
; * level-name (Goblin Lair , Halfling Village, Demon Kingdom)
; * spawn-rate
; * level-size
; * to finish lvl maybe find 3-4 signs to activate (takes some time) to open a portal
; every sign will increase spawn rate (maybe 0 at beginning -> can keep spawning)

; can use different algorithms(e.g. cave, module-gen-uf-terrain, room-gen? , differnt cave algorithm ...)

(defn- uf-place-creatures! [context spawn-rate tiled-map spawn-positions]
  (let [layer (add-layer! tiled-map :name "creatures" :visible true)
        creatures (all-properties context :properties/creatures)
        level (inc (rand-int 6))
        creatures (creatures-with-level creatures level)]
    ;(println "Level: " level)
    ;(println "Creatures with level: " (count creatures))
    (doseq [position spawn-positions
            :when (<= (rand) spawn-rate)]
      (set-tile! layer position (creature->tile (rand-nth creatures))))))

(def ^:private ->tm-tile
  (memoize
   (fn ->tm-tile [texture-region movement]
     {:pre [#{"all" "air" "none"} movement]}
     (let [tile (->static-tiled-map-tile texture-region)]
       (put! (tiled/properties tile) "movement" movement)
       tile))))

(def ^:private sprite-size 48)

(defn- terrain-texture-region [ctx]
  (->texture-region (ctx/cached-texture ctx "maps/uf_terrain.png")))

(defn- ->uf-tile [ctx & {:keys [sprite-x sprite-y movement]}]
  (->tm-tile (->texture-region (terrain-texture-region ctx)
                               [(* sprite-x sprite-size)
                                (* sprite-y sprite-size)
                                sprite-size
                                sprite-size])
             movement))

; TODO unused
(def ^:private ground-sprites [1 (range 5 11)])

(def ^:private uf-grounds
  (for [x [1 5]
        y (range 5 11)
        :when (not= [x y] [5 5])] ;wooden
    [x y]))

(def ^:private uf-walls
  (for [x [1]
        y [13,16,19,22,25,28]]
    [x y]))

(defn- ->ground-tile [ctx [x y]]
  (->uf-tile ctx :sprite-x (+ x (rand-0-3)) :sprite-y y :movement "all"))

(defn- ->wall-tile [ctx [x y]]
  (->uf-tile ctx :sprite-x (+ x (rand-0-5)) :sprite-y y :movement "none"))

(defn- ->transition-tile [ctx [x y]]
  (->uf-tile ctx :sprite-x (+ x (rand-0-5)) :sprite-y y :movement "none"))

(defn- transition? [grid [x y]]
  (= :ground (get grid [x (dec y)])))

(def ^:private uf-caves-scale 4)

(defn uf-caves [ctx {:keys [world/map-size world/spawn-rate]}]
  (let [{:keys [start grid]} (->cave-grid :size map-size)
        ;_ (println "Start: " start)
        ;_ (printgrid grid)
        ;_ (println)
        scale uf-caves-scale
        grid (mapgen.utils/scalegrid grid scale)
        ;_ (printgrid grid)
        ;_ (println)
        start-position (mapv #(* % scale) start)
        grid (reduce #(assoc %1 %2 :transition) grid
                     (adjacent-wall-positions grid))
        _ (assert (or
                   (= #{:wall :ground :transition} (set (grid/cells grid)))
                   (= #{:ground :transition}       (set (grid/cells grid))))
                  (str "(set (grid/cells grid)): " (set (grid/cells grid))))
        ;_ (printgrid grid)
        ;_ (println)
        ground-idx (rand-nth uf-grounds)
        {wall-x 0 wall-y 1 :as wall-idx} (rand-nth uf-walls)
        transition-idx  [wall-x (inc wall-y)]
        position->tile (fn [position]
                         (case (get grid position)
                           :wall (->wall-tile ctx wall-idx)
                           :transition (if (transition? grid position)
                                         (->transition-tile ctx transition-idx)
                                         (->wall-tile ctx wall-idx))
                           :ground (->ground-tile ctx ground-idx)))
        tiled-map (mapgen.tiled-utils/wgt-grid->tiled-map grid position->tile)

        can-spawn? #(= "all" (tiled/movement-property tiled-map %))
        _ (assert (can-spawn? start-position)) ; assuming hoping bottom left is movable
        spawn-positions (flood-fill grid start-position can-spawn?)
        ]
    ; TODO don't spawn my faction vampire w. items ...
    ; TODO don't spawn creatures on start position
    ; (all check have HP/movement..../?? ?) (breaks potential field, targeting, ...)
    (uf-place-creatures! ctx spawn-rate tiled-map spawn-positions)
    {:tiled-map tiled-map
     :start-position start-position}))
