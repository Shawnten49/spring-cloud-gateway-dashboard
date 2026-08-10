package com.example.gatewaydemo.route;

record RouteConfigRow(String routeId, String uri, int order,
                      String predicatesJson, String filtersJson, String metadataJson) {
}
