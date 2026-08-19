package dev.batch.bm_b010;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.batch.repository.bm.BookDataRepository;
import dev.common.entity.DataEntity;
/**
 * static_dataへのINSERTを、JDBCのSAVEPOINTを使ったネストトランザクション内で実行するための専用クラス。
 *
 * PostgreSQLはトランザクション内の1文がエラーになると、その例外をJava側でcatchして握りつぶしても
 * トランザクション自体はabortedのままになり、以降の全てのSQL文が
 * "current transaction is aborted, commands ignored until end of transaction block" で
 * 失敗し続けてしまう。
 *
 * このメソッドを Propagation.NESTED で呼び出すことで、呼び出し前にSAVEPOINTが張られ、
 * 例外が発生した場合はそのSAVEPOINTまでだけロールバックされる。これにより、
 * 呼び出し元（FinGettingStat#finGettingStat）の大きなトランザクション自体は
 * abortedにならず、正常に処理を継続・commitできる。
 *
 * 【重要】DuplicateKeyExceptionのcatchは、必ずこのメソッドの外側（別クラス）で行うこと。
 * 同じメソッド内でcatchしてしまうと、SpringのAOPが例外の伝播を検知できず
 * SAVEPOINTへのロールバックが行われない。
 */
@Component
public class StaticDataInsertExecutor {
	@Autowired
	private BookDataRepository bookDataRepository;
	@Transactional(propagation = Propagation.NESTED, transactionManager = "bmTxManager", rollbackFor = Exception.class)
	public int insert(DataEntity entity) {
		return bookDataRepository.insert(entity);
	}
}