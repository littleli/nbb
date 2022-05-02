(ns example
  (:require 
   [deps]
   [matchete.core :as m]))

(m/matches '{:vector [_ {:id ?id}]} 
           {:vector [{:id 1} {:id 2}]})
