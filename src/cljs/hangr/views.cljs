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

(defn friends-list-item
  [conv]
  [:li.conversation
   (str (map 
          :fallback_name
          (-> conv :conversation :participant_data)))])

(defn friends-list
  []
  (let [convs (subscribe [:convs])]
    (fn []
      [:ul#conversations
       (let [convs @convs]
         (println "CONVS" (count convs))
         (if (seq convs)
           (for [c convs] 
             ^{:key (:id c)} [friends-list-item c])
           "Loading, or no conversations"))])))

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
