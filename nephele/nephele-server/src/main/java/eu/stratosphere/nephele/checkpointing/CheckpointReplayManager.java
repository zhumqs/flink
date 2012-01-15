/***********************************************************************************************************************
 *
 * Copyright (C) 2010 by the Stratosphere project (http://stratosphere.eu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 **********************************************************************************************************************/

package eu.stratosphere.nephele.checkpointing;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import eu.stratosphere.nephele.configuration.GlobalConfiguration;
import eu.stratosphere.nephele.executiongraph.ExecutionVertexID;
import eu.stratosphere.nephele.taskmanager.transferenvelope.TransferEnvelopeDispatcher;

public class CheckpointReplayManager implements ReplayFinishedNotifier {

	private static final Log LOG = LogFactory.getLog(CheckpointReplayManager.class);

	public static final String CHECKPOINT_DIRECTORY_KEY = "channel.checkpoint.directory";

	public static final String DEFAULT_CHECKPOINT_DIRECTORY = "/tmp";

	/**
	 * The prefix for the name of the file containing the checkpoint meta data.
	 */
	public static final String METADATA_PREFIX = "checkpoint";

	private final TransferEnvelopeDispatcher transferEnvelopeDispatcher;

	private final String checkpointDirectory;

	private final ConcurrentMap<ExecutionVertexID, CheckpointReplayTask> runningReplayTasks;

	public CheckpointReplayManager(final TransferEnvelopeDispatcher transferEnvelopeDispatcher) {

		this.transferEnvelopeDispatcher = transferEnvelopeDispatcher;

		this.checkpointDirectory = GlobalConfiguration
			.getString(CHECKPOINT_DIRECTORY_KEY, DEFAULT_CHECKPOINT_DIRECTORY);

		this.runningReplayTasks = new ConcurrentHashMap<ExecutionVertexID, CheckpointReplayTask>();
	}

	public boolean hasCompleteCheckpointAvailable(final ExecutionVertexID vertexID) {

		final File file = new File(this.checkpointDirectory + File.separator + METADATA_PREFIX + "_" + vertexID
			+ "_final");
		if (file.exists()) {
			return true;
		}

		return false;
	}

	public boolean hasPartialCheckpointAvailable(final ExecutionVertexID vertexID) {

		File file = new File(this.checkpointDirectory + File.separator + METADATA_PREFIX + "_" + vertexID + "_0");
		if (file.exists()) {
			return true;
		}

		file = new File(this.checkpointDirectory + File.separator + METADATA_PREFIX + "_" + vertexID + "_part");
		if (file.exists()) {
			return true;
		}

		return false;
	}

	public void replayCheckpoint(final ExecutionVertexID vertexID) {

		final CheckpointReplayTask newReplayTask = new CheckpointReplayTask(this, vertexID, this.checkpointDirectory,
			this.transferEnvelopeDispatcher, hasCompleteCheckpointAvailable(vertexID));

		final CheckpointReplayTask runningReplayTask = this.runningReplayTasks.put(vertexID, newReplayTask);
		if (runningReplayTask != null) {
			LOG.info("There is already a replay task running for task " + vertexID + ", cancelling it first...");
			runningReplayTask.cancelAndWait();
		}

		LOG.info("Replaying checkpoint for vertex " + vertexID);
		newReplayTask.start();
	}

	/**
	 * Removes the checkpoint of the vertex with the given ID. All files contained in the checkpoint are deleted.
	 * 
	 * @param vertexID
	 *        the vertex whose checkpoint shall be removed
	 */
	public void removeCheckpoint(final ExecutionVertexID vertexID) {
		File file = new File(this.checkpointDirectory + File.separator + METADATA_PREFIX + "_" + vertexID
				+ "_final");
		if (file.exists()) {
			file.delete();
			return;
		}
		file = new File(this.checkpointDirectory + File.separator + METADATA_PREFIX + "_" + vertexID + "_0");
		if (file.exists()) {
			file.delete();
		}

		file = new File(this.checkpointDirectory + File.separator + METADATA_PREFIX + "_" + vertexID + "_part");
		if (file.exists()) {
			file.delete();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void replayFinished(final ExecutionVertexID vertexID) {

		this.runningReplayTasks.remove(vertexID);
	}
}
