(ns ^{:author "Daniel Leong"
      :doc "Views"}
  hangr.views
  (:require [reagent.core  :as reagent]
            [re-frame.core :refer [subscribe dispatch]]))

;; -- Conversation Page -------------------------------------------------------

(defn conversation
  [id]
  [:div "Hello, " id])

;; -- Friends List ------------------------------------------------------------

(defn friends-list
  []
  [:div "Frieeeends!"])

;; -- No-such-page handler ----------------------------------------------------

(defn four-oh-four
  []
  [:div 
   {:class "error"}
   "Woops! That doesn't exist"])

;; -- Main Switch -------------------------------------------------------------

(defn main
  []
  (let [page (subscribe [:page])]
    (fn []
      (let [[page args] @page]
        (case page
          :friends [friends-list]
          :conv [conversation (first args)]
          [four-oh-four])))))
