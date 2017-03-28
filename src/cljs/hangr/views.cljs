(ns ^{:author "Daniel Leong"
      :doc "Views"}
  hangr.views
  (:require [clojure.string :as string]
            [reagent.core  :as reagent]
            [re-frame.core :refer [subscribe dispatch]]
            [hangr.util.notification :refer [msg->notif]]
            [hangr.views.about :refer [about-page]]
            [hangr.views.conversation :refer [conversation conversation-header]]
            [hangr.views.friends-list :refer [friends-list friends-header]]
            [hangr.views.widgets :refer [icon]]))

;; -- Loading Spinner ---------------------------------------------------------

(defn spinner
  [reason]
  [:div#loading
   [:div.loader]
   reason])

;; -- No-such-page handler ----------------------------------------------------

(defn four-oh-four
  []
  [:div.error "Woops! That doesn't exist"])

;; -- Main Switch -------------------------------------------------------------

(defn main
  []
  (let [page (subscribe [:page])
        reconnecting? (subscribe [:reconnecting?])
        focused? (subscribe [:focused?])]
    (fn []
      (let [[page arg] @page
            reconnecting? @reconnecting?]
        (if (= :about page)
          ; the about page is special
          [about-page]
          [:div
           {:class (str
                     (when-not @focused?
                       "unfocused ")
                     (when reconnecting?
                       "disconnected"))}
           [:div#title
            (case page
              :conv [conversation-header arg]
              :friends [friends-header]
              "Hangr")
            (when reconnecting?
              [icon :signal-wifi-off.disconnected])]
           [:div#app-container
            (case page
              :connecting [spinner "Connecting"]
              :loading [spinner "Loading"]
              :friends [friends-list]
              :conv [conversation arg]
              (do
                (.warn js/console "Unknown page" (str page))
                [four-oh-four]))]])))))
