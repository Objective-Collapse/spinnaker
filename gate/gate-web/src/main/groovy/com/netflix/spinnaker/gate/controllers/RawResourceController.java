/*
 * Copyright 2020 Coveo, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.gate.controllers;

import com.netflix.spinnaker.gate.services.RawResourceService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
public class RawResourceController {
  private final RawResourceService rawResourceService;

  @Autowired
  public RawResourceController(RawResourceService rawResourceService) {
    this.rawResourceService = rawResourceService;
  }

  @Operation(summary = "Retrieve a list of raw resources for a given application")
  @RequestMapping(value = "/applications/{application}/rawResources", method = RequestMethod.GET)
  List<Map<String, Object>> getApplicationRawResources(@PathVariable String application) {
    return rawResourceService.getApplicationRawResources(application);
  }
}
