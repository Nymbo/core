#_(ns gdl.simple
  (:require [core.component :refer [defcomponent] :as component]
            [app.start :as app]
            [core.context :as ctx]))

#_(defcomponent :gdl/simple
  (component/create [[_ {:keys [font logo]}] ctx]
    {:special-font (ctx/generate-ttf ctx font)
     :logo (ctx/create-image ctx logo)}))

#_(defn app []
  (app/start {:app {:title "gdl demo"
                    :width 800
                    :height 600
                    :full-screen? false}
              :context [[:context/graphics true]
                        [:context/assets true]
                        [:context/image-drawer-creator true]
                        [:context/ui true]
                        [:context/screens {:first-screen :gdl/simple-screen
                                           :screens {:gdl/simple-screen true}}]
                        [:gdl/simple {:logo "logo.png"
                                      :font {:file "exocet/films.EXL_____.ttf"
                                             :size 16}}]]}))
