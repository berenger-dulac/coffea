package net.sourceforge.coffea.uml2.model.creation;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import net.sourceforge.coffea.uml2.CoffeaUML2Plugin;
import net.sourceforge.coffea.uml2.IUML2RunnableWithProgress;
import net.sourceforge.coffea.uml2.Resources;
import net.sourceforge.coffea.uml2.model.IElementService;
import net.sourceforge.coffea.uml2.model.IModelService;
import net.sourceforge.coffea.uml2.model.IPackageService;
import net.sourceforge.coffea.uml2.model.ITypeService;
import net.sourceforge.coffea.uml2.model.ITypesContainerService;
import net.sourceforge.coffea.uml2.model.impl.ClassService;
import net.sourceforge.coffea.uml2.model.impl.ClassifierService;
import net.sourceforge.coffea.uml2.model.impl.InterfaceService;
import net.sourceforge.coffea.uml2.model.impl.ModelService;
import net.sourceforge.coffea.uml2.model.impl.PackageService;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.ui.IWorkbenchWindow;

/**
 * Creates an <a href="http://www.omg.org/technology/documents/formal/uml.htm">
 * <em>UML</em></a> model from <a href="http://www.java.net"><em>Java</em></a> 
 * project. This can be done using the AST parser or via an 
 * {@link IJavaProject}.
 */
public class ModelServiceBuilder 
implements IModelServiceBuilding {

	/** Suffix of a <em>Java</em> file */
	public static final String javaFileSuffix = 
		Resources.getParameter("constants.javaFileSuffix");

	/** Default package name */
	public static final String defaultPackageName = 
		Resources.getParameter("constants.defaultPackageName");

	/**
	 * Project name, when a file is parsed takes the file name
	 * @see #parseFile(File)
	 */
	protected String modelName;

	/** Model */
	protected IModelService model;

	/** Source workbench window */
	protected IWorkbenchWindow workbenchWindow;

	/** Source view identifier in the source workbench window */
	protected String sourceViewIdentifier;

	/** The <em>Java</em> project from which the model must be built */
	protected IJavaProject javaProject;
	
	/** Model builder construction */
	public ModelServiceBuilder() {
		modelName = defaultPackageName;
	}

	/** 
	 * Model builder construction
	 * @param vid
	 * Value of {@link #sourceViewIdentifier}
	 */
	public ModelServiceBuilder(String vid, IWorkbenchWindow win) {
		this();
		if(vid == null)throw new NullPointerException();
		if(win == null)throw new NullPointerException();
		sourceViewIdentifier = vid;
		workbenchWindow = win;
	}

	/** Coffee worker initialization */
	public void init() {
	}

	// @Override
	public IModelService parseFile(File target) {
		init();
		if(target!=null) {
			if(getModelName()==null) {
				this.setModelName(target.getName());
			}
			model = new ModelService(this);
			parse(target);
		}
		return model;
	}	

	// @Override
	public IModelService buildModelService(IJavaElement el) {
		return buildModelService(el, null);
	}

	// @Override
	public IModelService buildModelService(String path) {
		IResource resource = 
			ResourcesPlugin.getWorkspace().getRoot().findMember(
					new Path(path)
			);
		if(resource instanceof IJavaElement) {
			IJavaElement el = (IJavaElement)resource;
			return buildModelService(el, null);
		}
		else return null;
	}

	// @Override
	public IModelService buildModelService(
			IJavaElement el, 
			IProgressMonitor monitor
	) {
		if(monitor==null) {
			monitor = new NullProgressMonitor();
		}
		IUML2RunnableWithProgress runnable = new ModelProcessorRunnable(el);
		try {
			runnable.run(monitor);
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return model;
	}

	/** Runnable processor producing a model from a Java element */
	public class ModelProcessorRunnable implements IUML2RunnableWithProgress {

		/** Java element from which a model is processed */
		private IJavaElement el;

		/** Model produced by the processor */
		private IModelService processedModel;

		/**
		 * Runnable construction
		 * @param e
		 * Value of {@link #el}
		 */
		public ModelProcessorRunnable(IJavaElement e) {
			el = e;
		}

		public void run(IProgressMonitor pm)
		throws InvocationTargetException, InterruptedException {
			processedModel = processJavaElement(el, pm);
		}

		/**
		 * Returns {@link #processedModel}
		 * @return Value of {@link #processedModel}
		 */
		public IModelService getProcessedModel() {
			return processedModel;
		}
	}

	public IModelService buildModelService(IJavaProject p) {
		model = new ModelService(this);
		model.setJavaProject(p);
		return model;
	}

	/**
	 * Builds a model service from a Java element using a progress monitor
	 * @param el
	 * Java element from which the model service must be built
	 * @param pm
	 * Progress monitor monitoring the operation
	 * @return Built model service
	 */
	protected IModelService processJavaElement(
			IJavaElement el, 
			IProgressMonitor pm
	) {
		if(pm==null) {
			pm = new NullProgressMonitor();
		}
		model = null;
		init();
		try {
			if(el!=null) {
				if(model==null) {
					IJavaProject p = null;
					// Resolving the first package fragment in the given 
					// element parents
					while(
							(el!=null)
							&&(!(el instanceof IPackageFragment))
					) {
						el = el.getParent();
					}
					// Once we have a package fragment, 
					if(el instanceof IPackageFragment) {
						// We get the project
						p = el.getJavaProject();
						// And the model name
						String name = el.getElementName();
						if((name==null)||(name.length()==0)) {
							name = IModelService.defaultPackageFileName;
						}
						setModelName(name);
					}
					if(p!=null) {
						IJavaModel m = p.getJavaModel();
						m.open(new SubProgressMonitor(pm, 1));
						buildModelService(p);
						IPackageFragment processPack = (IPackageFragment)el;
						processPackageFragment(processPack, pm);
						IPackageFragment[] packs = p.getPackageFragments();
						if(packs!=null) {
							IPackageFragment pack = null;
							String processedPackage = 
								processPack.getElementName();
							// If we have the default package, 
							// then we are processing all sources
							boolean sources = (
									(processedPackage!=null)
									&&(processedPackage.length()==0)
							);
							// For each package of the project
							int nbPackagesFragments = packs.length;
							pm.beginTask(
									Resources.getMessage(
											"labels.processingPackages"
									), 
									nbPackagesFragments
							);
							// resolveClasspath(p);
							for(int i=0 ; i<packs.length ; i++) {
								pack = packs[i];
								if(pack!=null) {
									String packName = pack.getElementName();
									// In the case we are processing all 
									// sources, 
									if(sources) {
										// We will process the package if it 
										// contains sources
										if(
												pack.getKind()
												==IPackageFragmentRoot.K_SOURCE
										) {
											processPackageFragment(
													pack, 
													new 
													SubProgressMonitor(
															pm, 
															1
													)
											);
										}
									}
									else {
										// Else we are processing only one 
										// package
										// If the package name contains the 
										// processed package name, 
										if(
												packName.contains(
														processedPackage
												)
										) {
											// Then it musn't be the package 
											// itself
											if(
													!packName.equals(
															processedPackage
													)
											) {
												// It must be a direct sub 
												// package
												String simpleName = 
													packName.substring(
															processedPackage
															.length()
													);
												if(
														simpleName
														.lastIndexOf('.')
														==0
												) {
													processPackageFragment(
															pack, 
															new 
															SubProgressMonitor(
																	pm, 
																	1
															)
													);
												}
											}
										}
									}
								}
								pm.worked(1);
							}
						}
					}
				}
			}
		} catch (JavaModelException e) {
			CoffeaUML2Plugin.getInstance().logError(e.getMessage(), e);
		}
		finally {
			// pm.worked(1);
		}
		return model;
	}

	/**
	 * Resolves a project's class path and registers it in the model 
	 * @param prj
	 * Project which class path must be resolved
	 */
	protected void resolveClasspath(IJavaProject prj) {
		if(prj!=null) {
			IModelService mdH = getLatestModelServiceBuilt();
			if(mdH!=null) {
				try {
					IPackageFragment[] packs = 
						prj.getPackageFragments();
					IPackageFragment pack = null;
					IClassFile[] classFiles = null;
					IClassFile classFile = null;
					IType type = null;
					String classFullName = null;
					if(packs!=null) {
						for(int i=0 ; i<packs.length ; i++) {
							pack = packs[i];
							if(
									(pack!=null)
									&&(
											!(
													pack.getKind()
													==IPackageFragmentRoot
													.K_SOURCE
											)
									)
							) {
								classFiles = pack.getClassFiles();
								if(classFiles!=null) {
									for(int j=0 ; j<classFiles.length ; j++) {
										classFile = classFiles[j];
										if(classFile!=null) {
											type = classFile.getType();
											if(type!=null) {
												classFullName = 
													ClassifierService
													.buildFullyQualifiedName(
															type
													);
												mdH.resolveTypeService(
														classFullName
												);
											}
										}
									}
								}
							}
						}
					}
				} catch (JavaModelException e) {
					e.printStackTrace();
				}
			}
		}
	}

	protected void processPackageFragment(
			IPackageFragment pack, 
			IProgressMonitor pm
	) {
		try {
			if(pack!=null) {
				ICompilationUnit[] comps = 
					pack.getCompilationUnits();
				if((comps!=null)&&(comps.length>0)) {
					int nbUnits = comps.length;
					try {
						pm.beginTask(
								Resources.getMessage(
										"labels.processingUnits"
								), 
								nbUnits
						);
						for(int j=0 ; j<comps.length ; j++) {
							processTypeService(
									comps[j], 
									new SubProgressMonitor(pm, 1)
							);
						}
					}
					finally {
						pm.done();
					}
				}
				else if(pack.getKind()==IPackageFragmentRoot.K_SOURCE) { 
					String name = pack.getElementName();
					if((name==null)||(name.length()==0)) {
						name = IModelService.defaultPackageFileName;
					}
					IPackageService group = 
						model.resolvePackageService(name);
					if(group==null) {
						new PackageService(
								pack, 
								model
						);
					}
				}
			}
		} catch (JavaModelException e) {
			CoffeaUML2Plugin.getInstance().logError(e.getMessage(), e);
		} finally {
			if(pm!=null) {
				pm.worked(1);
			}
		}
	}

	public IPackageService buildPackageService(
			IPackageFragment packageFragment
	) {
		IModelService projectModelService = 
			buildModelService(packageFragment);
		IElementService elementSrv = 
			projectModelService.getElementService(
					packageFragment.getElementName()
			);
		if(elementSrv instanceof IPackageService) { 
			return (IPackageService)elementSrv;
		}
		else {
			return null;
		}
	}

	public synchronized ITypeService<?, ?> buildTypeService(
			ICompilationUnit c, 
			IProgressMonitor mon
	) {
		IJavaProject project = c.getJavaProject();
		buildModelService(project);
		return processTypeService(c, mon);
	}

	public synchronized ITypeService<?, ?> processTypeService(
			ICompilationUnit c, 
			IProgressMonitor mon
	) {
		IType t = null;
		ITypeService<?, ?> service = null;
		ITypeService<?, ?> primaryTypeService = null;
		try {
			if(c!=null) {
				IType[] typesArray = c.getAllTypes();
				IType primaryType = c.findPrimaryType();
				List<IType> types = new ArrayList<IType>();
				// The primary type must be in first position in order to 
				// use it as container for the others...
				types.add(primaryType);
				// We add then all the other types
				for(int i=0 ; i<typesArray.length ; i++) {
					t = typesArray[i];
					if(
							(t!=null)
							&&(primaryType!=null)
							&&(!t.equals(primaryType))
					) {
						types.add(t);
					}
				}
				t = null;
				if(types!=null) {
					for(int i=0 ; i<types.size() ; i++) {
						t = types.get(i);
						if(t!=null) {
							// In a first step, we are going to process 
							// the class container
							ITypesContainerService group = null;
							// If the parent is the compilation unit, 
							if(
									(t.getParent()==c)
									&&(t.getPackageFragment()!=null)
							) {
								String name = 
									t.getPackageFragment().getElementName();
								if((name==null)||(name.length()==0)) {
									name = IModelService.defaultPackageFileName;
								}
								// Then we have the primary type, the 
								// container will be the package
								group = model.resolvePackageService(name);
								if(group==null) {
									group = 
										new PackageService(
												t.getPackageFragment(), 
												model
										);
								}
							}
							// Else if we have the primary type handler, 
							else if(
									(primaryTypeService!=null)
									&&(
											primaryTypeService instanceof 
											ITypesContainerService)

							) {
								// Then we are processing a nested class, 
								// the container will be this primary type 
								// handler
								group = 
									(ITypesContainerService)
									primaryTypeService;
							}
							else {
								// In any other case, we put it directly in 
								// the model
								group = model;
							}
							// Second step, we can process the type
							try {
								if(t.isInterface()) {
									service = 
										new InterfaceService(
												t, 
												group, 
												c
										);
								}
								else {
									service = 
										new ClassService(
												t, 
												group, 
												c
										);
								}
							} catch (JavaModelException e) {
								CoffeaUML2Plugin.getInstance().logError(
										e.getMessage(), 
										e
								);
							}
						}
						// If we are in first position of the list, 
						if(i==0) {
							// Then we note the primary type handler
							primaryTypeService = service;
						}
					}
				}
			}
		} catch (JavaModelException e) {
			CoffeaUML2Plugin.getInstance().logError(e.getMessage(), e);
		} finally {
			if(mon!=null) {
				mon.worked(1);
			}
		}
		// If we have a primary type service, 
		if(primaryTypeService != null) {
			// Then we return it because he is the containing element
			return primaryTypeService;
		}
		else {
			// Else we return the latest built service
			return service;
		}
	}

	public synchronized ITypeService<?, ?> processParsedType(
			TypeDeclaration t, 
			CompilationUnit u
	) {
		ITypeService<?, ?> result = null;
		// 1°) In a first step, we are going to process the package 
		// declaration
		ITypesContainerService group;
		if(u.getPackage()!=null) {
			group = 
				model.resolvePackageService(
						u.getPackage().getName().getFullyQualifiedName()
				);
			if(group==null) {
				group = 
					new PackageService(
							u.getPackage(), 
							model
					);
			}
		}
		else group = model;
		// 2°) Second step, we build a rewriter from the compilation unit
		AST ast = u.getAST();
		ImportDeclaration id = ast.newImportDeclaration();
		id.setName(ast.newName(new String[] {"java", "util", "Set"}));
		ASTRewrite rewriter = ASTRewrite.create(ast);
		// 3°) Third step, we can process the type declaration
		if(t.isInterface()) {
			result = new InterfaceService(t, group, rewriter, u);
		}
		else {
			result = new ClassService(t, group, rewriter, u);
		}
		return result;
	}

	/**
	 * Process an element of the file system (file or directory
	 * @param elm
	 * The element to be processed
	 */
	public void process(File elm) {
		this.readElement(elm, null);
	}

	/**
	 * Read an element of the file system (file or directory)
	 * @param elm
	 * The element to be read
	 * @param container
	 * Container directory
	 */
	protected void readElement(File elm, File container) {
		//We adapt the reading technique considering the element type (file or 
		//directory)
		if(elm.isFile())this.readFile(elm, container);
		else if(elm.isDirectory())this.readDirectory(elm, container);
	}

	/**
	 * Read a directory
	 * @param dir
	 *  Directory to be read
	 * @param container
	 *  Container directory to be read, <code>null</code> if it's in the root 
	 *  <em>package</em>
	 */
	protected void readDirectory(File dir, File container) {
		//If the file is a directory,
		if(dir.isDirectory()) {
			//Then we can process any of the elements it contains...
			File[] content = dir.listFiles();
			for(int i=0 ; i<content.length ; i++) {
				//...reading any of them
				File elm = content[i];
				this.readElement(elm, dir);
			}
		}
		else throw new IllegalArgumentException();
	}

	/**
	 * Read a file
	 * @param fil
	 * File to be read
	 * @param container
	 * Directory containing the file to be read, <code>null</code> if it is in 
	 * the root <em>package</em>
	 */
	protected void readFile(File fil, File container) {
		//If the file name has a suffix indicating it is a Java Files,
		if(fil.getName().lastIndexOf(ModelServiceBuilder.javaFileSuffix)!=-1) {
			//Then we are going to read it as a Java File
			try {
				this.readJavaFile(fil, container);
			} catch(IllegalArgumentException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Parses a Java source file
	 * @param parseFile
	 * 	The Java source file to parse
	 * @param container
	 * 	Container directory
	 * @throws IllegalArgumentException 
	 * 	If the file cannot be read (the specified <em>filJ</em> parameter is 
	 * 	not a file or the user account executing the program is not allowed to 
	 * 	read it)
	 */
	protected void readJavaFile(File parseFile, File container) 
	throws IllegalArgumentException {
		IUML2RunnableWithProgress runnable = new Parser(parseFile);
		CoffeaUML2Plugin.getInstance().execute(runnable);
	}

	/** The AST parser runnable */
	class Parser implements IUML2RunnableWithProgress {

		/** The <em>Java</em> file to parse */
		protected File parseFile;

		/** 
		 * AST parser runnable construction
		 * @param jFile
		 * 	Value of {@link #parseFile}
		 */
		Parser(File jFile) {
			parseFile = jFile;
		}

		public void run(IProgressMonitor monitor) {
			try {
				monitor.beginTask(parseFile.getName(), 6);
				IProject project = 
					ResourcesPlugin.getWorkspace().getRoot().getProject(
							getModelName()
					);
				monitor.worked(1);
				// If the file exists and can be read,
				if((parseFile.isFile())&&(parseFile.canRead())) {
					// Then we can extract its content
					try {
						FileReader flReader = new FileReader(parseFile);
						monitor.worked(2);
						char[] readPart = new char[5000];
						StringBuffer buffer = new StringBuffer();
						int nbRead;
						// Reading the content...
						try {
							while(
									(nbRead = 
										flReader.read(
												readPart, 
												0, 
												readPart.length
										)
									) >= 0
							) {
								buffer.append(readPart,0,nbRead);
							}
							flReader.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
						// Processing the content
						try {
							// If the processed code project has not been 
							// registered, 
							if(
									(javaProject==null)
									||
									(
											!javaProject.getElementName()
											.equals(getModelName())
									)
							){
								// Then we get it
								javaProject = 
									(IJavaProject)project.getNature(
											JavaCore.NATURE_ID
									);
								if(javaProject==null) {
									javaProject = JavaCore.create(project);
								}
								javaProject.open(null);
								getLatestModelServiceBuilt().setJavaProject(
										javaProject
								);
							}
						} catch (JavaModelException e1) {
							e1.printStackTrace();
						} catch (CoreException e2) {
							e2.printStackTrace();
						}
						monitor.worked(3);
						// If we have found the processed project, 
						if (javaProject != null) {
							// Then we parse the processed code section
							ASTParser parser = ASTParser.newParser(AST.JLS4);
							parser.setProject(javaProject);
							parser.setResolveBindings(true);
							char[] charArray = new char[buffer.length()];
							buffer.getChars(0, buffer.length(), charArray, 0);
							parser.setUnitName(javaProject.getElementName());
							parser.setSource(charArray);
							monitor.worked(4);
							CompilationUnit result = 
								(CompilationUnit)parser.createAST(null);
							monitor.worked(5);
							List<?> types = result.types();
							for(int i=0 ; i<types.size() ; i++) {
								ASTNode node = (ASTNode)types.get(i);
								if(node instanceof TypeDeclaration) {
									TypeDeclaration type = 
										(TypeDeclaration)node;
									/*
									 * We now know a type contained inside the 
									 * compilation unit
									 * Then the following manufacturing 
									 * operation will consist in adding this 
									 * type to the model
									 */
									processParsedType(
											type, 
											result
									);
								}
							}
							monitor.worked(6);
						}
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					}
				}
				else throw new IllegalArgumentException();		
			}
			finally {
				monitor.done();
			}
		}
	}

	public IModelService getLatestModelServiceBuilt() {
		init();
		return model;
	}

	public void save(String uri, String name) {
		save(uri, name, null);
	}

	public void save(String uri, String name, IProgressMonitor mn) {
		init();
		if(mn == null) {
			mn = new NullProgressMonitor();
		}
		mn.beginTask("labels.buildingModel", 10);
		model.setUpUMLModelElement();
		mn.worked(5);
		model.setName(name);
		model.createModelFile(uri, new SubProgressMonitor(mn, 5));
	}

	/**
	 * <em>Java</em> code file parsing
	 * @param f
	 * File to parse
	 */
	public synchronized void parse(File f) {
		if(f!=null) {
			process(f);
		}
	}

	/** @return Model name */
	public String getModelName() {
		return modelName;
	}

	/**
	 * @param n
	 * Model name
	 */
	public void setModelName(String n) {
		modelName = n;
	}

	// @Override
	public String getSourceViewId() {
		return sourceViewIdentifier;
	}
	
	// @Override
	public IWorkbenchWindow getSourceWorkbenchWindow() {
		return workbenchWindow;
	}
}