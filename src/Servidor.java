
import java.util.Queue;
import java.util.TreeSet;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.LinkedBlockingQueue;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.PriorityQueue;


/**
 * Implementa la clase Servidor que gestiona todas las conexiones y los trabajos
 * @author marmol
 *
 */
public class Servidor implements Runnable {
	private int puerto;
	private Queue<Trabajo> trabajosPorRealizar;
	private Map<UUID, Trabajo> trabajosRealizando;
	private TreeSet<Trabajo> trabajosRealizados;
	private LinkedList<Thread> clientes;
	private ServerSocket serverSocket;
	private boolean finalizado;
	int nClientesFinalizados = 0;
	int nClientesTotales = 0;
	int N;
	int divisiones;
	String dir;
	
	/**
	 * Constructor
	 * @param puerto
	 * @param divisiones
	 * @param xC
	 * @param yC
	 * @param size
	 * @param N
	 * @param maxIt
	 * @param dir
	 * @throws Exception
	 */
	Servidor(int puerto, int divisiones, double xC, double yC, int size, int N, int maxIt, String dir) throws Exception {
		this.N = N;
		this.dir = dir;
		
		trabajosPorRealizar = Trabajo.generarCola(divisiones, xC, yC, size, N, maxIt);
		trabajosRealizando = new HashMap<UUID, Trabajo>();
		
		Comparator<Trabajo> comparador = new Comparator<Trabajo>(){
			public int compare(Trabajo t1, Trabajo t2){
				return t1.getPosicion() < t2.getPosicion() ? -1 :
					(t1.getPosicion() == t2.getPosicion() ? 0 :
						1);
			}
		};
		
		trabajosRealizados = new TreeSet<Trabajo>(comparador);
		
		clientes = new LinkedList<Thread>();
		
		this.puerto = puerto;
		this.divisiones = divisiones;
		
		this.serverSocket = new ServerSocket(this.puerto);
		this.finalizado = false;
	}
	
	/**
	 * Comprueba si los trabajos estan completados
	 * @return
	 */
	public synchronized boolean estanTrabajosCompletados(){
		return trabajosPorRealizar.isEmpty() && trabajosRealizando.isEmpty();
	}
	
	/**
	 * Comprueba si hay trabajos por realizar
	 * @return
	 */
	public synchronized boolean hayTrabajosPorRealizar(){
		return !trabajosPorRealizar.isEmpty();
	}
	
	/**
	 * Saca un trabajo sin realizar, lo mete en realizando y devuelve el Trabajo
	 * @return
	 */
	public synchronized Trabajo sacarTrabajoSinRealizar(){
		if (trabajosPorRealizar.isEmpty()){
			return null;
		} else {
			Trabajo actual = trabajosPorRealizar.poll();
			trabajosRealizando.put(actual.id, actual);
			notificarCambiosTrabajosRealizando();
			return actual;
		}
	}
	
	/**
	 * Añade un trabajo que estaba siendo realizado y ahora esa completado (procesado) por un Cliente
	 * @param t El trabajo
	 * @throws Exception
	 */
	public synchronized void anhadirTrabajoRealizado(Trabajo t) throws Exception {
		if (trabajosRealizando.containsKey(t.id)){
			trabajosRealizados.add(t);
			trabajosRealizando.remove(t.id);
			notificarCambiosTrabajosRealizando();
			if (estanTrabajosCompletados()){
				this.notify();
			}
		} else {
			throw new Exception("El trabajo no se está realizando");
		}
	}
	
	/**
	 * Devuelve un trabajo que estaba siendo realizado pero que no pudo ser completado (error en Cliente)
	 * @param t El trabajo
	 */
	public synchronized void devolverTrabajoRealizando(Trabajo t){
		trabajosRealizando.remove(t.id);
		trabajosPorRealizar.add(t);
		notificarCambiosTrabajosRealizando();
	}
	
	/**
	 * Duerme un proceso hasta que se produzcan cambios los trabajos que se están realizando
	 */
	public void esperarCambiosTrabajosRealizando(){
		synchronized(trabajosRealizando){
			try {
				trabajosRealizando.wait();
			} catch (Exception e){}
		}
	}
	
	/**
	 * Notifica que se han producido cambios en los trabajos que se están realizando
	 */
	public void notificarCambiosTrabajosRealizando(){
		synchronized(trabajosRealizando){
			trabajosRealizando.notifyAll();
		}
	}
	
	/**
	 * Integra todos los trabajos realizados en una única imagen usando PGM
	 * @throws Exception
	 */
	private synchronized void  integrarTrabajosRealizados() throws Exception {
		PGM imagen = new PGM(dir, this.N, this.N, 255);
		
		for (Trabajo t: trabajosRealizados){
			imagen.anhadir(t.getMatriz());
		}
		
		imagen.cerrar();
	}
	
	/**
	 * Notifica que todos los trabajos se finalizaron
	 * @throws UnknownHostException
	 * @throws IOException
	 */
	public synchronized void finalizar() throws UnknownHostException, IOException {
		nClientesFinalizados++;
		if (nClientesFinalizados == nClientesTotales){
			finalizado = true;
			this.serverSocket.close();
		}
	}
	
	/**
	 * Incrementa el número de clientes totales
	 */
	private synchronized void nuevoCliente(){
		nClientesTotales++;
	}
	
	/**
	 * Comprueba si el Servidor puede finalizar
	 * @return
	 */
	private boolean podemosFinalizar(){
		return finalizado; 
	}
	
	/**
	 * Gestiona las conexiones entrantes no se acaben los trabajos
	 */
	public void run() {
		int intentos = 0;
		Socket s;
		Thread nuevoThread;
			
		while(!podemosFinalizar() && intentos < 5){
			try {
				try {
					s = this.serverSocket.accept();
				} catch (Exception e){
					if (!this.finalizado) throw e;
					else break;
				}
				nuevoCliente();
				nuevoThread = new Thread(new ServidorThread(s, this));
				clientes.add(nuevoThread);
				nuevoThread.start();
				intentos=0;
			} catch (Exception e){
				intentos++;
				e.printStackTrace();
			}
		}
		
		if (intentos < 5) {
			try {
				integrarTrabajosRealizados();
			} catch (Exception e){
				System.out.println("Se ha producido un error al integrar los trabajos realizados:");
				e.printStackTrace();
			}
		}
			

		for (Thread t : clientes) {
			try {t.join();}
			catch (Exception e){
				e.printStackTrace();
			}
		}
		
		System.out.println("Servidor thread finalizado");
	}
	
	/**
	 * Programa Servidor principal
	 * @param args Argumentos: puerto divisiones xCentro yCentro tamaño iteraciones archivo
	 */
	public static void main(String[] args)  {
		
		
		try {
			if (args.length < 7){
				throw new RuntimeException("Argumentos: puerto divisiones xCentro yCentro tamaño iteraciones archivo");
			}
			
			int puerto = Integer.parseInt(args[0]);
			int divisiones = Integer.parseInt(args[1]);
			double xCentro = Double.parseDouble(args[2]);
			double yCentro = Double.parseDouble(args[3]);
			int size = Integer.parseInt(args[4]);
			int maxIt = Integer.parseInt(args[5]);
			String dir = args[6];
			
			Servidor serv = new Servidor(puerto, divisiones, xCentro, yCentro, size, size, maxIt, dir);
			
			Thread serverThread = new Thread(serv);
			serverThread.start();
			
			try {
				serverThread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		} catch (Exception e){
			e.printStackTrace();
		}
		
		System.out.println("Servidor finalizado");
	}
}
