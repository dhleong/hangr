(ns ^{:author "Daniel Leong"
      :doc "About Hangr page"}
  hangr.views.about
  (:require [re-frame.core :refer [subscribe dispatch]]
            [hangr.util.ui :refer [click-dispatch]]
            [hangr.util.ui.markdown :refer [markdown->hiccup]]
            [hangr.util.updates :refer [current-version
                                        latest-version-download-url]]
            [hangr.views.widgets :refer [icon]]))

(def repo-url "https://github.com/dhleong/hangr")

(defn about-page
  []
  (let [latest-version @(subscribe [:latest-version])
        latest-notes @(subscribe [:latest-version-notes])]
    [:div#about-container
     [:div.title "Hangr"]
     [:div.version current-version]
     [:div
      [:button
       {:on-click #(dispatch [:open-external repo-url])}
       "Hangr Homepage"]]
     [:div.update-notification
      (if latest-version
        ; yes, update available!
        [:div
         [:a
          {:on-click (click-dispatch [:open-external
                                      latest-version-download-url])}
          [:span.clickable.header
           [icon :wb-sunny]
           [:span.label "New update available!"]
           [:div.click-to-download "Click to download"]]]
         (when latest-notes
           [:div.whats-new
            [:div.header "What's new?"]
            (markdown->hiccup
              :div
              latest-notes
              {:github repo-url
               :update {:a (fn [a]
                             (let [opts (second a)]
                               (assoc
                                 a 1
                                 {:href "#"
                                  :on-click
                                  (click-dispatch
                                    [:open-external
                                     (:href opts)])})))}})])]
        ; latest version:
        [:div.header "You have the latest version!"])]]))
