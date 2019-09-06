package org.ergoplatform.nodeView.history.components.popow

import io.iohk.iodb.ByteArrayWrapper
import org.ergoplatform.modifiers.history.{Extension, PoPowAlgos, PoPowHeader, PoPowProof, PoPowProofPrefix}
import org.ergoplatform.nodeView.history.components.{BasicReaders, HeadersComponent}
import org.ergoplatform.settings.{Algos, PoPowParams}
import scorex.util.{ScorexLogging, idToBytes, bytesToId}

import scala.util.{Failure, Try}

trait ProvingPoPowComponent extends EmptyPoPowComponent {
  self: HeadersComponent with BasicReaders with ScorexLogging =>

  val BestProofId = ByteArrayWrapper(Algos.hash("best_proof"))

  override def prove(params: PoPowParams): Try[PoPowProof] =
    bestHeaderOpt
      .fold[Try[PoPowProof]](Failure(new Exception("Empty chain"))) { bestHeader =>
        val chain = headerChainBack(Int.MaxValue, bestHeader, _.isGenesis).headers
        val poPowChain = chain.flatMap { h =>
          typedModifierById[Extension](h.extensionId)
            .flatMap(ext => PoPowAlgos.unpackInterlinks(ext.fields).toOption)
            .map(PoPowHeader(h, _))
        }
        Try(PoPowAlgos.prove(poPowChain)(params))
      }
      .map { proof =>
        historyStorage.getIndex(BestProofId)
          .flatMap(id => typedModifierById[PoPowProofPrefix](bytesToId(id)))
          .foreach(prefix => historyStorage.remove(Seq(prefix.id, prefix.suffixId)))
        historyStorage.insert(Seq(BestProofId -> idToBytes(proof.id)), Seq(proof.prefix, proof.suffix))
        proof
      }

}
