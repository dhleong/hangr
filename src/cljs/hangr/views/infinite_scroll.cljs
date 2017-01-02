;; ADAPTED FROM: https://gist.github.com/nberger/b5e316a43ffc3b7d5e084b228bd83899
;; 
(ns hangr.views.infinite-scroll
  (:require [reagent.core :as r]))

(defn- safe-component-mounted? [component]
  (try (boolean (r/dom-node component)) (catch js/Object _ false)))

;; TODO what we actually want is "throttle," so an event can get through every N millis,
;;  instead of constantly resetting the timer
(defn debounce
  "Returns a function that will call f only after threshold has passed without new calls
  to the function. Calls prep-fn on the args in a sync way, which can be used for things like
  calling .persist on the event object to be able to access the event attributes in f"
  ([threshold f] (debounce threshold f (constantly nil)))
  ([threshold f prep-fn]
   (let [t (atom nil)]
    (fn [& args]
      (when @t (js/clearTimeout @t))
      (apply prep-fn args)
      (reset! t (js/setTimeout #(do
                                  (reset! t nil)
                                  (apply f args))
                               threshold))))))

;; NOTE: this ONLY works for reverse scroll now (IE toward the page top)
(defn infinite-scroll
  "props is a map with :can-show-more? & :load-fn keys"
  [props]
  (let [listener-fn (atom nil)
        pre-update-scroll-height (atom nil)
        scroll-getter (or (:scroll-getter props)
                          #(.-parentNode %))
        get-scroll-target (fn [this]
                            (scroll-getter (r/dom-node this)))
        detach-scroll-listener (fn [this]
                                 (when-let [listener @listener-fn]
                                   (let [scroll-target (get-scroll-target this)]
                                     (.removeEventListener scroll-target "scroll" listener)
                                     (.removeEventListener scroll-target "resize" listener))
                                   (reset! listener-fn nil)))
        scroll-listener (fn [this]
                          (when (safe-component-mounted? this)
                            (let [{:keys [load-fn]} (r/props this)
                                  scroll-target (get-scroll-target this)
                                  scroll-top (.-scrollTop scroll-target)
                                  threshold 50
                                  should-load-more? (< scroll-top
                                                       threshold)]
                              (when should-load-more?
                                (println "loading more...")
                                (detach-scroll-listener this)
                                (load-fn)))))
        debounced-scroll-listener (debounce 200 scroll-listener)
        attach-scroll-listener (fn [this & [updated?]]
                                 (let [{:keys [can-show-more?]} (r/props this)
                                       scroll-target (get-scroll-target this)]
                                   (when updated?
                                     ; preserve scroll position
                                     (let [scroll-diff (- (.-scrollHeight scroll-target)
                                                          @pre-update-scroll-height)]
                                       (when (> scroll-diff 0)
                                         (set! (.-scrollTop scroll-target)
                                               (+ (.-scrollTop scroll-target)
                                                  scroll-diff)))))
                                   (when can-show-more?
                                     (when-not @listener-fn
                                       (let [listener (reset! listener-fn (partial debounced-scroll-listener this))]
                                         (.addEventListener scroll-target "scroll" listener)
                                         (.addEventListener scroll-target "resize" listener))))))]
    (r/create-class
      {:component-did-mount
       (fn [this]
         (attach-scroll-listener this))
       :component-will-update
       (fn [this _]
         (reset! pre-update-scroll-height (.-scrollHeight (get-scroll-target this))))
       :component-did-update
       (fn [this _]
         (attach-scroll-listener this :updated))
       :component-will-unmount
       detach-scroll-listener
       :reagent-render
       (fn [props]
         [:div.infinite-scroller])})))
