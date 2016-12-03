(ns ^{:author "Daniel Leong"
      :doc "Views"}
  hangr.views
  (:require [reagent.core  :as reagent]
            [re-frame.core :refer [subscribe dispatch]]))

;; -- Loading Spinner ---------------------------------------------------------

(defn spinner
  [reason]
  [:div#loading
   [:div.loader]
   reason])

;; -- Conversation Page -------------------------------------------------------

(defn conversation
  [id]
  [:div "Hello, " id])

(defn conversation-title
  [id]
  [:span id])

;; -- Friends List ------------------------------------------------------------

(defn friends-list
  []
  [:div "Frieeeends!"])

;; -- No-such-page handler ----------------------------------------------------

(defn four-oh-four
  []
  [:div.error "Woops! That doesn't exist"])

;; -- Main Switch -------------------------------------------------------------

(defn main
  []
  (let [page (subscribe [:page])]
    (fn []
      (let [[page args] @page]
        [:div
         [:div#title 
          (case page
            :conv [conversation-title (first args)]
            "Hangr")]
         [:div#app-container
          (case page
            :connecting [spinner "Connecting..."]
            :friends [friends-list]
            :conv [conversation (first args)]
            [four-oh-four])]]))))
