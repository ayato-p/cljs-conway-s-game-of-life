(ns life-game.core
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [clojure.string :as str]
            [clojure.browser.dom :as dom]
            [reagent.core :as r :refer [atom]]
            [re-frame.core :refer [dispatch
                                   dispatch-sync
                                   after
                                   path
                                   register-handler
                                   register-sub
                                   subscribe]]))

;;; initialize

(defn new-state [width height]
  (vec (repeatedly width #(vec (repeatedly height (constantly false))))))

(def initial-state
  {:step 0
   :state (new-state 100 100)})

(def all-positions
  (for [i (range 100) j (range 100)] [i j]))

;;; helper functions

(defn get-around [x y]
  (for [x' [-1 0 1]
        y' [-1 0 1]
        :when (not= 0 x' y')]
    [(+ x x') (+ y y')]))

(defn alive? [state x y]
  (let [life (get-in state [x y])
        lives (reduce #(+ %1 (if (get-in state %2) 1 0)) 0
                      (get-around x y))]
    (or (and life (#{2 3} lives))
        (and (not life) (= lives 3)))))

;;; event handlers

(register-handler
 ::initialize
 (after #(dispatch [::randomize]))
 (fn [db _]
   (merge db initial-state)))

(register-handler
 ::step-up
 (path [:step])
 (fn [step _]
   (inc step)))

(register-handler
 ::next
 (path [:state])
 (fn [state _]
   (let [width (count (first state))
         height (count state)
         f (partial apply alive? state)]
     (reduce #(update-in %1 %2
                         (fn [life] (f %2)))
             state
             all-positions))))

(register-handler
 ::randomize
 (path [:state])
 (fn [state _]
   (let [width (count (first state))
         height (count state)]
     (reduce #(assoc-in %1 %2 (not (zero? (int (rand 2)))))
             state
             all-positions))))

(register-handler
 ::tick!
 (fn [db _]
   (dispatch [::step-up])
   (dispatch [::next])
   db))

;;; subscription handlers

(register-sub
 ::state
 (fn [db _]
   (reaction (:state @db))))

(register-sub
 ::step
 (fn [db _]
   (reaction (:step @db))))

;;; component

(defn life [x y live?]
  [:rect {:x x
          :y y
          :width 1
          :height 1
          :stroke "black"
          :stroke-width 0.01
          :rx 0.1
          :fill (if live? :black :white)}])

(defn board []
  (let [current-state (subscribe [::state])
        state-width (count (first @current-state))
        state-height (count @current-state)]
    [:svg {:style {:width 1000
                   :height 1000
                   :border "1px solid black"}
           :view-box (str/join " " [0 0 50 50])}
     (into [:g]
           (for [i (range state-width)
                 j (range state-height)
                 :let [live? (get-in @current-state [i j])]]
             [life i j live?]))]))

(defn life-game-world []
  [:div
   [:span {:style {:display :block}}
    [:h1 {:style {:display :inline}} "Life Game : " (deref (subscribe [::step]))]]
   [board]])

(defn main []
  (when-let [elm (dom/get-element "life-game")]
    (dispatch-sync [::initialize])
    (r/render [life-game-world] elm)
    (js/setInterval #(dispatch [::tick!]) 200)))
