(ns storefront.platform.carousel-two
  (:require [sablono.core :refer-macros [html]]
            react-slick
            [om.core :as om]))

(defn inner-component [{:keys [slides settings]} owner _]
  (om/component
   (js/React.createElement js/Slider
                           (clj->js (merge {:pauseOnHover true
                                            ;; :waitForAnimate true
                                            ;; TODO: figure out why onMouseUp always
                                            ;; triggers navigation to link in slide,
                                            ;; while onTouchEnd doesn't. This prevents
                                            ;; us from allowing drag on desktop.
                                            :draggable    false}
                                           settings))
                           (html (for [[idx slide] (map-indexed vector slides)]
                                   ;; Wrapping div allows slider.js to attach
                                   ;; click handlers without overwriting ours
                                   [:div {:key idx} slide])))))

(defn cancel-autoplay [owner]
  (om/set-state! owner {:autoplay false}))

(defn override-autoplay [original autoplay-override]
  (update original :autoplay #(and % autoplay-override)))

(defn component [data owner _]
  (reify
    om/IInitState
    (init-state [_]
      {:autoplay true})

    om/IRenderState
    (render-state [_ {:keys [autoplay]}]
      (html
       ;; Cancel autoplay on interaction
       [:div {:on-mouse-down  #(cancel-autoplay owner)
              :on-touch-start #(cancel-autoplay owner)}
        (om/build inner-component (-> data
                                      (update-in [:settings] override-autoplay autoplay)
                                      (update-in [:settings :responsive]
                                                 (fn [responsive]
                                                   (map
                                                    (fn [breakpoint]
                                                      (update breakpoint :settings override-autoplay autoplay))
                                                    responsive)))))]))))