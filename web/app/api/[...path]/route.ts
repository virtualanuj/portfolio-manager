import { forward } from "@/lib/proxy";

export const dynamic = "force-dynamic";

export const GET = (request: Request) => forward(request);
export const POST = (request: Request) => forward(request);
export const PUT = (request: Request) => forward(request);
export const DELETE = (request: Request) => forward(request);
