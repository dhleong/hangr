(ns ^{:author "Daniel Leong"
      :doc "Views"}
  hangr.views
  (:require [clojure.string :as string]
            [reagent.core  :as reagent]
            [re-frame.core :refer [subscribe dispatch]]
            [hangr.util.notification :refer [msg->notif]]
            [hangr.views.conversation :refer [conversation conversation-title]]))

;; -- Loading Spinner ---------------------------------------------------------

(defn spinner
  [reason]
  [:div#loading
   [:div.loader]
   reason])

;; -- Friends List ------------------------------------------------------------

(defn friends-list-item
  [conv]
  (let [self (subscribe [:self])]
    (fn [conv]
      [:li.conversation
       {:on-click 
        #(dispatch [:select-conv (:id conv)])}
       [:div.name
        [conversation-title conv]]
       [:div.preview
        ;; FIXME: use a more appropriate function
        ;; TODO: support images
        (msg->notif (->> conv
                         :events
                         last))]])))

(defn friends-list
  []
  (let [convs (subscribe [:convs])]
    (fn []
      (let [convs @convs]
        (if (seq convs)
          ;; we have conversations
          [:ul#conversations
           (for [c convs] 
             ^{:key (:id c)} [friends-list-item c])]
          ;; nothing : ()
          [:div "No conversations"])))))

;; -- No-such-page handler ----------------------------------------------------

(defn four-oh-four
  []
  [:div.error "Woops! That doesn't exist"])

;; -- Main Switch -------------------------------------------------------------

(defn main
  []
  (let [page (subscribe [:page])]
    (fn []
      (let [[page arg] @page]
        [:div
         [:div#title 
          (case page
            :conv [conversation-title arg]
            "Hangr")]
         [:div#app-container
          (case page
            :connecting [spinner "Connecting"]
            :loading [spinner "Loading"]
            :friends [friends-list]
            :conv [conversation arg]
            (do
              (.warn js/console "Unknown page" (str page))
              [four-oh-four]))]]))))
