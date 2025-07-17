package dev.common.logger;

import org.springframework.stereotype.Component;

import dev.common.exception.BusinessException;
import dev.common.exception.SystemException;

/**
 * ロガー管理クラス
 * @author shiraishitoshio
 *
 */
@Component
public class ManageLoggerComponentImpl implements ManageLoggerComponent {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void init(String exeMode, String logicCd, String country, String league) {
		BookMakerLogger.init(exeMode, logicCd, country, league);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void init(String exeMode, String info) {
		BookMakerLogger.init(exeMode, info);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void clear() {
		BookMakerLogger.clear();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void debugInfoLog(String projectName, String className, String methodName, String messageCd,
			String... fillChar) {
		BookMakerLogger.info(projectName, className, methodName, messageCd, fillChar);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void debugWarnLog(String projectName, String className, String methodName, String messageCd,
			String... fillChar) {
		BookMakerLogger.warn(projectName, className, methodName, messageCd, fillChar);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void debugErrorLog(String projectName, String className, String methodName, String errorCode,
			Exception exception, String... fillChar) {
		BookMakerLogger.error(projectName, className, methodName, errorCode, exception, fillChar);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void debugStartInfoLog(String projectName, String className, String methodName,
			String... fillChar) {
		BookMakerLogger.info(projectName, className, methodName, null, fillChar);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void debugEndInfoLog(String projectName, String className, String methodName,
			String... fillChar) {
		BookMakerLogger.info(projectName, className, methodName, null, fillChar);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void createBusinessException(String projectName, String className, String methodName, String errorCode,
			Throwable exception) {
		throw new BusinessException(
				projectName,
				className,
				methodName,
				errorCode,
				exception);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void createSystemException(String projectName, String className, String methodName, String errorCode,
			Throwable exception) {
		throw new SystemException(
				projectName,
				className,
				methodName,
				errorCode,
				exception);
	}









}
