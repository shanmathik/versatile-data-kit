# Copyright (c) 2021 VMware, Inc.
# SPDX-License-Identifier: Apache-2.0
from __future__ import annotations

import pathlib
from dataclasses import dataclass
from typing import cast

from taurus.api.job_input import IJobArguments
from taurus.api.job_input import IJobInput
from taurus.api.job_input import ITemplate
from taurus.api.plugin.plugin_input import IIngesterRegistry
from taurus.api.plugin.plugin_input import IManagedConnectionRegistry
from taurus.api.plugin.plugin_input import IPropertiesRegistry
from taurus.api.plugin.plugin_input import ITemplateRegistry
from taurus.vdk.builtin_plugins.connection.impl.router import ManagedConnectionRouter
from taurus.vdk.builtin_plugins.ingestion.ingester_router import IngesterRouter
from taurus.vdk.builtin_plugins.job_properties.properties_router import PropertiesRouter
from taurus.vdk.builtin_plugins.run.step import StepBuilder
from taurus.vdk.core.context import CoreContext


# TODO: split into mutable and immutable job context (e.g JobContextBuilder and JobContext)
# JobContext is mutable only until initializing phase. After init phase only state store should be mutable.


@dataclass()
class JobContext:
    """
    Keep context about a Data Job run (execution).
    It is used by plugins (during job_initialize phase) to setup the data job with the necessary context.
    Things like what database connections are possible, what are the steps of the job and their order,
    how will they be executed, what is the templates that are registered to be used and so on.
    """

    # the name of the job
    name: str
    # Job directory
    job_directory: pathlib.Path
    # the context in which the job is initialized
    # Give access to configuration of the job and to state store.
    # Plugins can keep their own context in the statestore and access it later
    # Common job context - like setps, connections, templates, arguments is kept in JobContext directly
    # instead of in Statestore.
    core_context: CoreContext
    # the steps to be executed composed as a tree
    step_builder: StepBuilder
    # the supported connections
    connections: IManagedConnectionRegistry
    # initialize templates
    templates: ITemplateRegistry
    # job arguments passed by user for current execution
    job_args: IJobArguments
    # initialized job input
    job_input: IJobInput
    # initialize ingestion
    ingester: IIngesterRegistry
    # initialize ingestion
    properties: IPropertiesRegistry

    def __init__(
        self,
        name: str,
        job_directory: pathlib.Path,
        core_context: CoreContext,
        job_args: IJobArguments,
        templates: ITemplateRegistry,
    ):
        self.name = name
        self.job_directory = job_directory
        self.core_context = core_context
        self.step_builder = StepBuilder()
        self.job_args = job_args

        self.connections = ManagedConnectionRouter(core_context.configuration)
        self.templates = cast(ITemplateRegistry, templates)
        self.ingester = IngesterRouter(core_context.configuration, core_context.state)

        self.properties = PropertiesRouter(
            job_name=self.name, cfg=core_context.configuration
        )

        from taurus.vdk.builtin_plugins.run.job_input import JobInput

        self.job_input = JobInput(
            cast(ManagedConnectionRouter, self.connections),
            self.core_context.state,
            cast(PropertiesRouter, self.properties),
            self.job_args,
            cast(ITemplate, self.templates),
            cast(IngesterRouter, self.ingester),
        )