/*
 * HUMBOLDT: A Framework for Data Harmonisation and Service Integration.
 * EU Integrated Project #030962                 01.10.2006 - 30.09.2010
 * 
 * For more information on the project, please refer to the this web site:
 * http://www.esdi-humboldt.eu
 * 
 * LICENSE: For information on the license under which this program is 
 * available, please refer to http:/www.esdi-humboldt.eu/license.html#core
 * (c) the HUMBOLDT Consortium, 2007 to 2010.
 */

package eu.esdihumboldt.hale.schemaprovider.model;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.log4j.Logger;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.AttributeType;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.Name;

import com.vividsolutions.jts.geom.Geometry;

import eu.esdihumboldt.goml.align.Entity;
import eu.esdihumboldt.goml.omwg.FeatureClass;
import eu.esdihumboldt.goml.rdf.About;

/**
 * Represents a type definition
 *
 * @author Simon Templer
 * @partner 01 / Fraunhofer Institute for Computer Graphics Research
 * @version $Id$ 
 */
public class TypeDefinition extends AbstractDefinition implements Comparable<TypeDefinition>,
	Definition {
	
	private static final Logger log = Logger.getLogger(TypeDefinition.class);
	
	/**
	 * The type name
	 */
	private final Name name;

	/**
	 * The feature type representing the type
	 */
	private AttributeType type;
	
	/**
	 * The super type definition
	 */
	private final TypeDefinition superType;
	
	/**
	 * The subtypes
	 */
	private final SortedSet<TypeDefinition> subTypes = new TreeSet<TypeDefinition>();
	
	/**
	 * The elements referencing this type
	 */
	private final Set<SchemaElement> declaringElements = new HashSet<SchemaElement>();
	
	/**
	 * If the type is abstract
	 */
	private boolean abstractType = false; 
	
	private final boolean complex;
	
	/**
	 * The list of declared attributes
	 */
	private final SortedSet<AttributeDefinition> declaredAttributes = new TreeSet<AttributeDefinition>();
	
	/**
	 * The inherited attributes
	 */
	private SortedSet<AttributeDefinition> inheritedAttributes;

	/**
	 * Create a new type definition
	 * 
	 * @param name the type name 
	 * @param type the corresponding feature type, may be <code>null</code>
	 * @param superType the super type, may be <code>null</code>
	 */
	public TypeDefinition(Name name, AttributeType type, 
			TypeDefinition superType) {
		super();
		this.name = name;
		this.type = type;
		this.superType = superType;
		
		if (type != null && !(type instanceof FeatureType)) {
			complex = false;
		}
		else {
			complex = true;
		}
		
		if (superType != null) {
			superType.subTypes.add(this);
		}
	}
	
	/**
	 * Determines if this type actually represents a feature type that is based
	 * on AbstractFeatureType
	 * 
	 * @return if this definition represents a feature type
	 */
	public boolean isFeatureType() {
		AttributeType type = getType();
		
		if (type != null && !(type instanceof FeatureType)) {
			return false;
		}
		if (name.getLocalPart().equalsIgnoreCase("AbstractFeatureType")) {
			return true;
		}
		else if (getSuperType() == null) {
			return false;
		}
		else {
			return getSuperType().isFeatureType();
		}
	}
	
	/**
	 * Determines if this type represents a complex type
	 * 
	 * @return if this definition represents a complex type
	 */
	public boolean isComplexType() {
		return complex;
	}
	
	/**
	 * Add a declared attribute, the declaring type of the attribute will be set
	 * to this type
	 * 
	 * @param attribute the attribute definition
	 */
	public void addDeclaredAttribute(AttributeDefinition attribute) {
		attribute.setDeclaringType(this);
		declaredAttributes.add(attribute);
	}
	
	/**
	 * Removes a declared attribute
	 * 
	 * @param attribute
	 */
	public void removeDeclaredAttribute(AttributeDefinition attribute) {
		attribute.setDeclaringType(null);
		declaredAttributes.remove(attribute);
	}
	
	/**
	 * Add an element that references this type
	 * 
	 * @param element the element that references this type
	 */
	public void addDeclaringElement(SchemaElement element) {
		declaringElements.add(element);
	}
	
	/**
	 * Removes an element that references this type
	 * 
	 * @param element the element to remove
	 */
	public void removeDeclaringElement(SchemaElement element) {
		declaringElements.remove(element);
	}

	/**
	 * @return the name
	 */
	public Name getName() {
		return name;
	}

	/**
	 * @return the featureType
	 */
	public AttributeType getType() {
		if (type == null) {
			if (!declaringElements.isEmpty()) {
				//XXX grab first
				SchemaElement element = declaringElements.iterator().next();
				return element.getAttributeType();
			}
			else {
				type = createFeatureType(null);
			}
		}
		return type;
	}
	
	/**
	 * Get the feature type if available
	 * 
	 * @return the feature type or <code>null</code> if no type could be
	 *   determined or the type is not a feature type
	 */
	public FeatureType getFeatureType() {
		if (getType() != null && getType() instanceof FeatureType) {
			return (FeatureType) getType();
		}
		else {
			return null;
		}
	}

	/**
	 * Create the feature type from the super types and attributes, this method
	 *   will be called when there was no explicit type provided
	 *   
	 * @param name a custom name to use for the type (e.g. the element name)
	 *   or <code>null</code> 
	 * 
	 * @return the feature type
	 */
	public FeatureType createFeatureType(Name name) {
		SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
		
		if (getSuperType() != null) {
			// has super type
			if (getSuperType().getType() != null && getSuperType().getType() instanceof SimpleFeatureType) {
				builder.setSuperType((SimpleFeatureType) getSuperType().getType());
			}
			else {
				builder.setSuperType(null);
			}
		}
		else {
			builder.setSuperType(null);
		}
		
		// add all attributes
		TypeDefinition typeDef = this;
		while (typeDef != null) {
			// add attributes for current type
			for (AttributeDefinition attribute : typeDef.getDeclaredAttributes()) {
				TypeDefinition attrType = attribute.getAttributeType();
				if (this.equals(attrType)) {
					log.warn("Self referencing type: " + getName());
				}
				else {
					AttributeDescriptor desc = attribute.createAttributeDescriptor();
					if (desc != null) {
						builder.add(desc);
						
						if (Geometry.class.isAssignableFrom(desc.getType().getBinding())) {
							builder.setDefaultGeometry(desc.getName().getLocalPart());
						}
					}
				}
			}
			
			// switch to super type
			typeDef = typeDef.getSuperType();
		}
		
		// other properties
		builder.setAbstract(abstractType);
		
		builder.setName((name != null)?(name):(getName()));
		return builder.buildFeatureType();
	}

	/**
	 * @return the superType
	 */
	public TypeDefinition getSuperType() {
		return superType;
	}

	/**
	 * @return the declaredAttributes
	 */
	public Iterable<AttributeDefinition> getDeclaredAttributes() {
		return declaredAttributes;
	}
	
	/**
	 * Get the declared attributes and the super type attributes
	 * 
	 * @return the attribute definitions
	 */
	public Collection<AttributeDefinition> getAttributes() {
		Collection<AttributeDefinition> attributes = new TreeSet<AttributeDefinition>();
		
		// add declared attributes
		attributes.addAll(declaredAttributes);
		
		if (inheritedAttributes == null) {
			inheritedAttributes = new TreeSet<AttributeDefinition>();
			
			// populate inherited attributes
			TypeDefinition parent = getSuperType();
			while (parent != null) {
				for (AttributeDefinition parentAttribute : parent.getDeclaredAttributes()) {
					// create attribute definition copy
					AttributeDefinition attribute = parentAttribute.copyAttribute(this);
					inheritedAttributes.add(attribute);
				}
				
				parent = parent.getSuperType();
			}
		}
		
		// add inherited attributes
		attributes.addAll(inheritedAttributes);
		
		return attributes;
	}

	/**
	 * @return the abstractType
	 */
	public boolean isAbstract() {
		return abstractType;
	}

	/**
	 * @param abstractType the abstractType to set
	 */
	public void setAbstract(boolean abstractType) {
		this.abstractType = abstractType;
	}

	/**
	 * @return the subTypes
	 */
	public Collection<TypeDefinition> getSubTypes() {
		return subTypes;
	}

	/**
	 * @see Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	/**
	 * @see Object#equals(Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TypeDefinition other = (TypeDefinition) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

	/**
	 * @see Comparable#compareTo(Object)
	 */
	public int compareTo(TypeDefinition other) {
		int result = name.getLocalPart().compareToIgnoreCase(other.name.getLocalPart());
		if (result == 0) {
			return name.getNamespaceURI().compareToIgnoreCase(other.name.getNamespaceURI());
		}
		
		return result;
	}

	/**
	 * @see Definition#getIdentifier()
	 */
	public String getIdentifier() {
		return name.getNamespaceURI() + "/" + name.getLocalPart();
	}
	
	/**
	 * @see Definition#getEntity()
	 */
	public Entity getEntity() {
		return new FeatureClass(
				new About(name.getNamespaceURI(), 
						name.getLocalPart()));
	}

	/**
	 * @see Definition#getDisplayName()
	 */
	public String getDisplayName() {
		return getName().getLocalPart();
	}

	/**
	 * @return the declaringElements
	 */
	public Set<SchemaElement> getDeclaringElements() {
		return declaringElements;
	}
	
	/**
	 * @see Object#toString()
	 */
	@Override
	public String toString() {
		return "[type] " + getIdentifier();
	}

	/**
	 * Determine if the attribute type was already set
	 * 
	 * @return if the attribute type was set
	 */
	public boolean isAttributeTypeSet() {
		return type != null;
	}

	/**
	 * Set the attribute type
	 * 
	 * @param type the attribute type
	 */
	public void setType(AttributeType type) {
		this.type = type;
	}

}
