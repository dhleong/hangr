(ns ^{:author "Daniel Leong"
      :doc "Hangr Core"}
  hangr.core
  (:require-macros [secretary.core :refer [defroute]])
  (:require [goog.events :as events]
            [reagent.core :as reagent]
            [re-frame.core :refer [dispatch dispatch-sync]]
            [re-frisk.core :refer [enable-re-frisk!]]
            [secretary.core :as secretary]
            [hangr.events]
            [hangr.fx]
            [hangr.subs]
            [hangr.views]
            [hangr.connection :as connection]
            [devtools.core :as devtools])
  (:import [goog History]
           [goog.history EventType]))

;; -- Debugging aids ----------------------------------------------------------
(devtools/install!)       ;; we love https://github.com/binaryage/cljs-devtools
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
  (dispatch-sync [:initialize-db])
  #_(when js/goog.DEBUG 
    (enable-re-frisk!))
  (connection/init!)
  (reagent/render [hangr.views/main] (.getElementById js/document "app")))

(defn init!
  []
  (mount-root))
