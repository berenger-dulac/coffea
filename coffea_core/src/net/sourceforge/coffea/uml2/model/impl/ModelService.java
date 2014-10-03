package net.sourceforge.coffea.uml2.model.impl;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import net.sourceforge.coffea.uml2.CoffeaUML2Plugin;
import net.sourceforge.coffea.uml2.Resources;
import net.sourceforge.coffea.uml2.model.IElementService;
import net.sourceforge.coffea.uml2.model.IModelService;
import net.sourceforge.coffea.uml2.model.IPackageService;
import net.sourceforge.coffea.uml2.model.ITypeService;
import net.sourceforge.coffea.uml2.model.ITypesContainerService;
import net.sourceforge.coffea.uml2.model.creation.CreateModelRunnable;
import net.sourceforge.coffea.uml2.model.creation.IModelServiceBuilding;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.emf.common.command.Command;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.transaction.NotificationFilter;
import org.eclipse.emf.transaction.ResourceSetChangeEvent;
import org.eclipse.emf.transaction.RollbackException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.uml2.uml.Element;
import org.eclipse.uml2.uml.Model;
import org.eclipse.uml2.uml.UMLFactory;
import org.eclipse.uml2.uml.resource.UMLResource;

/** Service for a model */
public class ModelService 
extends ElementService<Model> 
implements IModelService {

	/** @see java.io.Serializables */
	private static final long serialVersionUID = 6187450089919544028L;

	/** <em>Java</em> project containing the model handled by the service */
	protected IJavaProject javaProject;

	/** List services for the packages belonging to the model */
	protected List<IPackageService> packages;

	/** List of model types handlers */
	protected List<ITypeService<?, ?>> types;

	/** Worker linking the model to Java code */
	protected IModelServiceBuilding creator;

	/** Package for primitive types */
	protected IPackageService primitiveTypesPackage;

	/** Package for class path types */
	protected IPackageService classpathTypesPackage;

	/** Class diagram EMF resource */
	// ? private org.eclipse.emf.ecore.resource.Resource classDiagramEMFResource;
	private IResource classDiagramEMFResource;
	
	/** Class diagram workspace resource */
	private IResource classDiagramWorkspaceResource;

	/** Boolean value indicating if the packages form a hierarchy */
	protected boolean packagesHierarchy;

	/**
	 * Construction of a model handler
	 * @param w
	 * Value of {@link #creator}
	 */
	public ModelService(IModelServiceBuilding w, IJavaProject jProject) {
		super();
		setJavaProject(jProject);
		packagesHierarchy = false;
		creator = w;
		packages = new ArrayList<IPackageService>();
		types = new ArrayList<ITypeService<?, ?>>();
		primitiveTypesPackage = 
			new PackageService(this, primitiveTypesPackageName);
		packages.remove(primitiveTypesPackage);
		classpathTypesPackage = 
			new PackageService(this, classpathTypesPackageName);
		packages.remove(classpathTypesPackage);
		ResourceSet set = new ResourceSetImpl();
		URI uri = createEmfUri();
		// TODO Load existing model here
		
	}
	
	// @Override
	public URI createEmfUri() {
		Model m = getUMLElement();
		if(m == null) {
			throw new IllegalStateException("The model should not be null");
		}
		String dirUri = getJavaProjectUriString();
		String name = m.getName();
		URI location = 
				URI.createURI("file://" + dirUri).appendSegment(name)
				.appendFileExtension(UMLResource.FILE_EXTENSION);
		return location;
	}

	// @Override
	public IModelService getModelService() {
		return this;
	}

	// @Override
	public IJavaProject getJavaProject() {
		return javaProject;
	}
	
	// @Override
	public String getJavaProjectUriString() {
		if(javaProject == null) {
			throw new IllegalStateException(
					"The Java project should not be null"
			);
		}
		return javaProject.getResource().getLocation().toOSString();
	}

	// @Override
	public void setJavaProject(IJavaProject p) {
		javaProject = p;
	}

	// @Override
	public String getSimpleName() {
		return umlModelElement.getName();
	}

	// @Override
	public String getFullName() {
		return getSimpleName();
	}

	/** Set up the package hierarchy from the content of {@link #packages} */
	protected void setUpPackageHierarchy() {
		if(packages != null) {
			IPackageService p = null;
			// In a first loop on the packages list, 
			for(int i = 0 ; i < packages.size() ; i++) {
				//Each package retrieves its parent
				p = packages.get(i);
				if(p != null) {
					p.retrieveContainerFromHierarchy();
				}
			}
			// Recursively each package retrieve its children
			List<IPackageService> root = fetchSubPackagesFromHierarchy();
			packages = root;
			packagesHierarchy = true;
		}
	}

	// @Override
	public void setUpUMLModelElement() {
		if(umlModelElement==null) {
			umlModelElement = UMLFactory.eINSTANCE.createModel();
			String name = javaProject.getElementName();
			umlModelElement.setName(name);
		}
		setUpPackageHierarchy();
		for(int i=0 ; i<packages.size() ; i++) {
			packages.get(i).setUpUMLModelElement();
		}
		for(int i=0 ; i<types.size() ; i++) {
			types.get(i).setUpUMLModelElement();
		}
	}

	// @Override
	public void addPackageService(IPackageService el) {
		packages.add(el);
	}

	// @Override
	public List<IPackageService> getPackagesServices() {
		return packages;
	}

	// @Override
	public IPackageService resolvePackageService(String n) {
		for (int i = 0; i < this.packages.size(); i++) {
			IPackageService pck = this.packages.get(i);
			String packFullName = pck.getFullName();
			if ((packFullName!=null)&&(packFullName.equals(n))) {
				return pck;
			}
			else {
				IPackageService el;
				if ((el = pck.resolvePackageService(n)) != null) {
					return el;
				}
			}
		}
		return null;
	}

	// @Override
	public ITypesContainerService getContainerService() {
		return null;
	}

	// @Override
	public void addTypeService(ITypeService<?, ?> clH) {
		types.add(clH);
	}

	// @Override
	public List<ITypeService<?, ?>> getTypesServices() {
		return types;
	}

	// @Override
	public ITypeService<?, ?> resolveTypeService(String n)  {
		ITypeService<?, ?> typ = null;
		if (n != null) {
			for(int i = 0 ; i<types.size() ; i++) {
				typ = types.get(i);
				if((typ!=null)&&(typ.getFullName().equals(n))) {
					return typ;
				}
			}
			// No interface found directly in the model, trying to get it from
			// packages
			for(int i=0 ; i<packages.size() ; i++) {
				typ = packages.get(i).resolveTypeService(n);
				if(typ!=null)return typ;
			}
			// The type was not in the packages
			// If the name starts with java.lang or contains no dot, 
			String primitiveTypes = 
				Resources.getCodeConstant(
						"constants.primitiveTypes"
				);
			if(
					(n.startsWith("java.lang"))
					||(primitiveTypes.contains(n))
			) {
				// Then we look for the type in the primitive types
				typ = primitiveTypesPackage.resolveTypeService(n);
				if(typ!=null) {
					return typ;
				}
				// We then have to create a primitive type
				typ = new ClassService(primitiveTypesPackage, n);
				// primitiveTypesPackage.addTypeHandler(typ);
			}
			else {
				// Else we look for the type in the class path
				typ = classpathTypesPackage.resolveTypeService(n);
				if(typ!=null) {
					return typ;
				}
				// We then have to create a class path type
				typ = new ClassService(classpathTypesPackage, n);
				// classpathTypesPackage.addTypeHandler(typ);
			}
		}
		return typ;
	}

	// @Override
	public boolean arePackageInHierarchy() {
		return packagesHierarchy;
	}

	// @Override
	public List<IPackageService> fetchSubPackagesFromHierarchy() {
		// Looping on the packages list, 
		List<IPackageService> rootPackages = 
			new ArrayList<IPackageService>();
		IPackageService p = null;
		for(int i=0 ; i<packages.size() ; i++) {
			// We identify which have directly the model for container
			p = packages.get(i);
			if((p!=null) &&(p.getContainerService().equals(this))) {
				// The package, which are "root packages", will be the 
				// basement for a recursive retrieving of sub packages
				p.fetchSubPackagesFromHierarchy();
				rootPackages.add(p);
			}
		}
		return rootPackages;
	}

	// @Override
	public void createModelFile() {
		CreateModelRunnable runnable = new CreateModelRunnable(this);
		CoffeaUML2Plugin.getInstance().execute(runnable);
	}

	// @Override
	public void createModelFile(IProgressMonitor monitor) {
		if(monitor==null) {
			monitor = new NullProgressMonitor();
		}
		CreateModelRunnable runnable =  new CreateModelRunnable(this);
		try {
			runnable.run(monitor);
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	// @Override
	public IElementService getElementHandler(Element el) {
		IElementService elH = null;
		if(el!=null) {
			String elFullName = ElementService.buildFullyQualifiedName(el);
			if(elFullName!=null) {
				elH = getElementService(elFullName);
			}
		}
		return elH;
	}

	// @Override
	public List<IElementService> getElementsHandlers() {
		List<IElementService> ret = new ArrayList<IElementService>();
		if(types!=null) {
			ITypeService<?, ?> t;
			for(int i=0 ; i<types.size() ; i++) {
				t = types.get(i);
				if(t!=null) {
					ret.add(t);
				}
			}
		}
		if(packages!=null) {
			IPackageService p;
			for(int i=0 ; i<packages.size() ; i++) {
				p = packages.get(i);
				if(p!=null) {
					ret.add(p);
				}
			}
		}
		return ret;	
	}

	@Override
	public IModelServiceBuilding getServiceBuilder() {
		return creator;
	}
	
	// @Override
	public IElementService getElementService(String n) {
		IElementService ret = null;
		if(n!=null) {
			if((ret==null)&&(types!=null)) {
				ITypeService<?, ?> t;
				for(int i=0 ; i<types.size() ; i++) {
					t = types.get(i);
					if(t!=null) {
						if(n.equals(t.getFullName())) {
							ret = t;
						}
						else {
							ret = t.getElementService(n);
						}
						if(ret!=null) {
							break;
						}
					}
				}
			}
			if((ret==null)&&(packages!=null)) {
				IPackageService p;
				for(int i=0 ; i<packages.size() ; i++) {
					p = packages.get(i);
					if(p!=null) {
						if(n.equals(p.getFullName())) {
							ret = p;
						}
						else {
							ret = p.getElementService(n);
						}
						if(ret!=null) {
							break;
						}
					}
				}
			}
		}
		return ret;	
	}

	// @Override
	public Element findEditorUMLElement() {
		return umlModelElement;
	}

	// @Override
	public void acceptModelChangeNotification(Notification nt) {
		// TODO Auto-generated method stub
		
	}

	// @Override
	public NotificationFilter getFilter() {
		// TODO Auto-generated method stub
		return null;
	}

	// @Override
	public Command transactionAboutToCommit(ResourceSetChangeEvent event)
			throws RollbackException {
		// TODO Auto-generated method stub
		return null;
	}

	// @Override
	public void resourceSetChanged(ResourceSetChangeEvent event) {
		// TODO Auto-generated method stub
		
	}

	// @Override
	public IResource getClassDiagramEMFResource() {
		return classDiagramEMFResource;
	}

	// @Override
	public IResource getClassDiagramWorkspaceResource() {
		return classDiagramWorkspaceResource;
	}
}