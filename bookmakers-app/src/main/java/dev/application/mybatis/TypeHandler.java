package dev.application.mybatis;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

@MappedJdbcTypes({JdbcType.VARCHAR, JdbcType.CHAR, JdbcType.LONGVARCHAR})
@MappedTypes(String.class)
public class TypeHandler extends BaseTypeHandler<String> {

  // 取得時に strip()（Unicode空白も除去）
  @Override
  public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
    String s = rs.getString(columnName);
    return s == null ? null : s.strip();
  }

  @Override
  public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    String s = rs.getString(columnIndex);
    return s == null ? null : s.strip();
  }

  @Override
  public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    String s = cs.getString(columnIndex);
    return s == null ? null : s.strip();
  }

  // 書き込み時も strip したいならここで実施（不要ならそのまま）
  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
    ps.setString(i, parameter == null ? null : parameter.strip());
  }
}
