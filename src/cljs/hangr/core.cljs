(ns ^{:author "Daniel Leong"
      :doc "Hangr Core"}
  hangr.core
  (:require-macros [secretary.core :refer [defroute]])
  (:require [goog.events :as events]
            [reagent.core :as reagent]
            [re-frame.core :refer [dispatch dispatch-sync 
                                   clear-subscription-cache!]]
            ;; [re-frisk.core :refer [enable-re-frisk!]]
            [secretary.core :as secretary]
            [hangr.events]
            [hangr.fx]
            [hangr.subs]
            [hangr.views]
            [hangr.connection :as connection])
  (:import [goog History]
           [goog.history EventType]))

;; -- Debugging aids ----------------------------------------------------------
(enable-console-print!)   ;; so println writes to console.log

;; -- Routes and History ------------------------------------------------------

(defroute "/" [] (dispatch [:navigate :friends]))
(defroute "/c/:conv-id" [conv-id] (dispatch [:navigate :conv conv-id]))

(def history
  (doto (History.)
    (events/listen EventType.NAVIGATE
                   (fn [event] (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

;; -- Mount and init ----------------------------------------------------------

(defn mount-root
  []
  ; core DB init
  (dispatch-sync [:initialize-db])
  ; clean up for hot reloads
  (clear-subscription-cache!)
  ;; (when js/goog.DEBUG 
  ;;   (enable-re-frisk!))
  ; init the connection
  (connection/init!)
  ; start rendering
  (reagent/render [hangr.views/main] (.getElementById js/document "app")))

(defn init!
  []
  ; track window focused-ness
  (set! (.-onfocus js/window) 
        #(dispatch [:set-focused true]))
  (set! (.-onblur js/window) 
        #(dispatch [:set-focused false]))
  (mount-root))
