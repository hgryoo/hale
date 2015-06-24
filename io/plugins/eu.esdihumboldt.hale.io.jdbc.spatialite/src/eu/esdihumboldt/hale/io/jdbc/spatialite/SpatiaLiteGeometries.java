package eu.esdihumboldt.hale.io.jdbc.spatialite;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.sqlite.SQLiteConnection;

import schemacrawler.schema.Column;
import schemacrawler.schema.ColumnDataType;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.io.WKTReader;
import com.vividsolutions.jts.io.WKTWriter;

import de.fhg.igd.slf4jplus.ALogger;
import de.fhg.igd.slf4jplus.ALoggerFactory;
import eu.esdihumboldt.hale.common.instance.geometry.DefaultGeometryProperty;
import eu.esdihumboldt.hale.common.instance.geometry.impl.CodeDefinition;
import eu.esdihumboldt.hale.common.instance.geometry.impl.WKTDefinition;
import eu.esdihumboldt.hale.common.schema.geometry.CRSDefinition;
import eu.esdihumboldt.hale.common.schema.geometry.GeometryProperty;
import eu.esdihumboldt.hale.common.schema.model.TypeDefinition;
import eu.esdihumboldt.hale.common.schema.model.impl.DefaultTypeDefinition;
import eu.esdihumboldt.hale.io.jdbc.GeometryAdvisor;
import eu.esdihumboldt.hale.io.jdbc.constraints.GeometryMetadata;
import eu.esdihumboldt.hale.io.jdbc.spatialite.internal.GeometryTypeMetadata;
import eu.esdihumboldt.hale.io.jdbc.spatialite.internal.SpatiaLiteHelper;
import eu.esdihumboldt.hale.io.jdbc.spatialite.internal.SpatiaLiteSupport;
import eu.esdihumboldt.hale.io.jdbc.spatialite.internal.SpatiaLiteSupportFactory;
import eu.esdihumboldt.hale.io.jdbc.spatialite.internal.SrsMetadata;

/**
 * Geometry advisor for SpatiaLite.
 * 
 * @author Stefano Costa, GeoSolutions
 */
public class SpatiaLiteGeometries implements GeometryAdvisor<SQLiteConnection> {

	private static final ALogger log = ALoggerFactory.getLogger(SpatiaLiteGeometries.class);

	@Override
	public boolean isFixedType(ColumnDataType columnType) {
		/*
		 * Concrete information on geometry type and SRS is not stored in the
		 * column but as metadata in the database. Therefore every column has to
		 * be configured on its own.
		 */
		return false;
	}

	@Override
	public Class<? extends Geometry> configureGeometryColumnType(SQLiteConnection connection,
			Column column, DefaultTypeDefinition type) {
		String colName = column.getName();
		String tabName = column.getParent().getName();
		SpatiaLiteSupport slSupport = SpatiaLiteSupportFactory.getInstance()
				.createSpatiaLiteSupport(connection);

		// warn if SpatiaLite is not available
		SpatiaLiteHelper.isSpatialLiteLoadedReport(connection, false);

		GeometryTypeMetadata geomTypeMeta = slSupport.getGeometryTypeMetadata(connection, tabName,
				colName);

		if (geomTypeMeta != null) {
			SrsMetadata srsMeta = slSupport.getSrsMetadata(connection, geomTypeMeta.getSrid());

			if (srsMeta != null) {
				// Create constraint to save the informations
				GeometryMetadata columnTypeConstraint = new GeometryMetadata(
						Integer.toString(srsMeta.getAuthSrid()), geomTypeMeta.getCoordDimension(),
						srsMeta.getSrText(), srsMeta.getAuthName());

				type.setConstraint(columnTypeConstraint);
			}

			return geomTypeMeta.getGeomType();
		}
		else {
			// no geometry column could be found
			return null;
		}
	}

	@Override
	public Object convertGeometry(GeometryProperty<?> geom, TypeDefinition columnType,
			SQLiteConnection connection) throws Exception {
		// show error and abort if SpatiaLite is not available
		if (!SpatiaLiteHelper.isSpatialLiteLoadedReport(connection, true)) {
			throw new IllegalStateException("SpatiaLite module is not available");
		}

		// Transform from sourceCRS to targetCRS
		GeometryMetadata columnTypeMetadata = columnType.getConstraint(GeometryMetadata.class);

		// transform
		CoordinateReferenceSystem targetCRS = null;
		if (columnTypeMetadata.getAuthName().equalsIgnoreCase("EPSG")) {
			targetCRS = CRS.decode(columnTypeMetadata.getAuthName() + ":"
					+ columnTypeMetadata.getSrs());
		}
		else {
			String wkt = columnTypeMetadata.getSrsText();
			if (wkt != null && !wkt.isEmpty()) {
				targetCRS = CRS.parseWKT(columnTypeMetadata.getSrsText());
			}
		}

		Geometry targetGeometry;
		if (targetCRS != null) {
			MathTransform transform = CRS.findMathTransform(geom.getCRSDefinition().getCRS(),
					targetCRS);
			targetGeometry = JTS.transform(geom.getGeometry(), transform);
		}
		else {
			targetGeometry = geom.getGeometry();
		}

		// encode JTS Geometry
		return encodeGeometryValue(targetGeometry, columnTypeMetadata, connection);
	}

	private Object encodeGeometryValue(Geometry value, GeometryMetadata metadata,
			SQLiteConnection connection) throws SQLException {
		// convert JTS geometry to SpatiaLite's internal BLOB format
		WKTWriter wktWriter = new WKTWriter(metadata.getDimension());
		/*
		 * FIXME does the WKT writer actually produce 3D geometries for geometry
		 * objects with three dimensional coordinates?
		 */
		String sqlGeomFromText = "SELECT GeomFromText(?, ?)";
		/*
		 * XXX is the SpatiaLite WKT parser robust enough to handle 3D
		 * geometries that don't have the proper geometry type code? The
		 * WKTWriter for example does produce "MULTIPOLGON" instead of
		 * "MULTIPOLYGON Z"
		 */

		PreparedStatement stmt = connection.prepareStatement(sqlGeomFromText);
		stmt.setString(1, wktWriter.write(value));
		stmt.setInt(2, Integer.valueOf(metadata.getSrs()));

		ResultSet rs = stmt.executeQuery();

		Object encodedValue = null;
		if (rs.next()) {
			encodedValue = rs.getObject(1);
		}

		return encodedValue;
	}

	@Override
	public GeometryProperty<?> convertToInstanceGeometry(Object geom, TypeDefinition columnType,
			SQLiteConnection connection) throws Exception {
		// show error and abort if SpatiaLite is not available
		if (!SpatiaLiteHelper.isSpatialLiteLoadedReport(connection, true)) {
			// don't throw, will prevent any data being loaded
//			throw new IllegalStateException("SpatiaLite module is not available");
		}

		// decode geometry read from DB
		GeometryMetadata columnTypeMetadata = columnType.getConstraint(GeometryMetadata.class);
		Geometry jtsGeom = decodeGeometryValue(geom, columnTypeMetadata, connection);

		// determine CRS
		CRSDefinition crsDef = null;
		if (columnTypeMetadata.getAuthName().equalsIgnoreCase("EPSG")) {
			String epsgCode = columnTypeMetadata.getAuthName() + ":" + columnTypeMetadata.getSrs();
			crsDef = new CodeDefinition(epsgCode, null);
		}
		else {
			crsDef = new WKTDefinition(columnTypeMetadata.getSrsText(), null);
		}

		return new DefaultGeometryProperty<Geometry>(crsDef, jtsGeom);
	}

	private Geometry decodeGeometryValue(Object geom,
			@SuppressWarnings("unused") GeometryMetadata metadata, SQLiteConnection connection)
			throws SQLException {
		// geom parameter is a byte[] in SpatiaLite's internal BLOB format;
		// for easy parsing with JTS, I must re-read geometry from DB in WKT or
		// WKB format

		/*
		 * We could use the WKT - but the JTS WKTReader does not support
		 * properly encoded 3D geometries (e.g. "MULTIPOLYON Z" instead of just
		 * "MULTIPOLYGON")
		 */
//		String sqlGeomAsWKX = "SELECT ST_AsText(?)";

		/*
		 * We could use the 2D WKT - but this will reduce all 3D geometries to a
		 * 2D projection.
		 */
//		String sqlGeomAsWKX = "SELECT AsWKT(?)";

		/*
		 * We could use the WKB - but the JTS WKBReader does not properly
		 * support geometry type codes of 1000 and above (e.g. 1007 for a
		 * GeometryCollection with Z coordinate)
		 */
//		String sqlGeomAsWKX = "SELECT ST_AsBinary(?)";

		/*
		 * We could use the EWKB - but the JTS WKBReader does not handle that
		 * properly as well (wrong geometry type extracted, 3D not recognized).
		 */
//		String sqlGeomAsWKX = "SELECT AsEWKB(?)";

		/*
		 * We can use the EWKT - but the JTS WKTReader will fail if there is a
		 * preceding SRID (which we can remove).
		 */
		String sqlGeomAsWKX = "SELECT AsEWKT(?)";

		PreparedStatement stmt = connection.prepareStatement(sqlGeomAsWKX);
		stmt.setObject(1, geom);

		ResultSet rs = stmt.executeQuery();

		Geometry jtsGeom = null;
		if (rs.next()) {
			// WKB
//			byte[] geomAsByteArray = rs.getBytes(1);
			// WKT
			String geomAsText = rs.getString(1);
			// remove SRID from EWKT
			if (geomAsText.startsWith("SRID")) {
				int index = geomAsText.indexOf(';');
				if (index >= 0 && index + 1 < geomAsText.length()) {
					geomAsText = geomAsText.substring(index + 1);
				}
			}

			// conversion to JTS via WKB/WKT
			GeometryFactory factory = new GeometryFactory();
//			WKBReader wkbReader = new WKBReader(factory);
			WKTReader wktReader = new WKTReader(factory);

			try {
//				jtsGeom = wkbReader.read(geomAsByteArray);
				jtsGeom = wktReader.read(geomAsText);
			} catch (Exception e) {
				log.error("Could not load geometry from database", e);
			}
		}

		return jtsGeom;
	}
}
