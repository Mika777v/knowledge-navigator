import { proxyToBackend } from "@/lib/bff/proxy";

type Context = RouteContext<"/api/[...path]">;

async function handle(request: Request, ctx: Context) {
  const { path } = await ctx.params;
  return proxyToBackend(request, path);
}

export const GET = handle;
export const POST = handle;
export const PUT = handle;
export const PATCH = handle;
export const DELETE = handle;
